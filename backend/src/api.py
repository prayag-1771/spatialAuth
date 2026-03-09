from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List
import numpy as np
import joblib
import os
from sklearn.preprocessing import StandardScaler
from sklearn.svm import OneClassSVM

app = FastAPI()

class EnrollmentRequest(BaseModel):
    room_id: str
    samples: List[List[float]]

class AuthRequest(BaseModel):
    room_id: str
    samples: List[List[float]]

def apply_weights(X_scaled):
    """
    Apply feature weighting.
    Indices 0,1,2: Magnetometer (Reduced weight)
    Indices 3,4,5: WiFi (Primary signal)
    Indices 6,7,8,9,10: Acoustic
    """
    X_weighted = X_scaled.copy()
    X_weighted[:, 0:3] *= 0.1  # Magnetometer weight reduced to 10%
    X_weighted[:, 3:6] *= 1.0  # WiFi weight remains 100%
    X_weighted[:, 6:11] *= 0.5 # Acoustic weight at 50%
    return X_weighted

def save_artifacts(model, scaler, room_id):
    room_folder = os.path.join("models", room_id)
    os.makedirs(room_folder, exist_ok=True)
    joblib.dump(model, os.path.join(room_folder, "model.pkl"))
    joblib.dump(scaler, os.path.join(room_folder, "scaler.pkl"))

def load_artifacts(room_id):
    room_folder = os.path.join("models", room_id)
    model_path = os.path.join(room_folder, "model.pkl")
    scaler_path = os.path.join(room_folder, "scaler.pkl")
    if not os.path.exists(model_path) or not os.path.exists(scaler_path):
        raise HTTPException(status_code=400, detail=f"Room '{room_id}' not enrolled.")
    model = joblib.load(model_path)
    scaler = joblib.load(scaler_path)
    return model, scaler

@app.get("/health")
def health():
    return {"status": "API running"}

@app.post("/enroll")
def enroll(request: EnrollmentRequest):
    samples = np.array(request.samples)
    if len(samples.shape) != 2:
        raise HTTPException(status_code=400, detail="Samples must be a 2D list.")
    
    # Add noise for robustness
    noise = np.random.normal(0, 0.02, samples.shape)
    samples_with_noise = samples + noise

    scaler = StandardScaler()
    samples_scaled = scaler.fit_transform(samples_with_noise)
    
    # Apply feature weights to reduce Magnetometer influence
    samples_weighted = apply_weights(samples_scaled)
    
    model = OneClassSVM(
        kernel="rbf",
        gamma=0.1, 
        nu=0.12 # Slightly more forgiving nu
    )
    
    model.fit(samples_weighted)
    save_artifacts(model, scaler, request.room_id)
    
    print(f"Room {request.room_id} enrolled. Mag weight reduced to 0.1.")
    return {
        "message": "Enrollment successful",
        "room_id": request.room_id,
        "samples_used": samples.shape[0]
    }

@app.post("/authenticate")
def authenticate(request: AuthRequest):
    samples = np.array(request.samples)
    model, scaler = load_artifacts(request.room_id)
    
    samples_scaled = scaler.transform(samples)
    
    # Apply same feature weights during authentication
    samples_weighted = apply_weights(samples_scaled)
    
    predictions = model.predict(samples_weighted)
    positive_count = np.sum(predictions == 1)
    
    decision = "ACCEPT" if positive_count >= 3 else "REJECT"
    
    print(f"Auth for {request.room_id}: {decision} ({positive_count}/5 positive)")
    print(f"Predictions: {predictions.tolist()}")
    
    return {
        "room_id": request.room_id,
        "decision": decision,
        "positive_votes": int(positive_count),
        "predictions": predictions.tolist()
    }

@app.get("/rooms")
def list_rooms():
    models_folder = "models"
    if not os.path.exists(models_folder):
        return {"rooms": []}
    rooms = [n for n in os.listdir(models_folder) if os.path.isdir(os.path.join(models_folder, n))]
    return {"rooms": rooms}
