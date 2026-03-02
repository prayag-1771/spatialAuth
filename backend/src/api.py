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
    if samples.shape[1] != 11:
        raise HTTPException(status_code=400, detail="Each sample must have 11 features.")
    if samples.shape[0] < 20:
        raise HTTPException(status_code=400, detail="Minimum 20 samples required.")
    scaler = StandardScaler()
    samples_scaled = scaler.fit_transform(samples)
    model = OneClassSVM(
        kernel="rbf",
        gamma="scale",
        nu=0.05
    )
    model.fit(samples_scaled)
    save_artifacts(model, scaler, request.room_id)
    return {
        "message": "Enrollment successful",
        "room_id": request.room_id,
        "samples_used": samples.shape[0]
    }
@app.post("/authenticate")
def authenticate(request: AuthRequest):
    samples = np.array(request.samples)
    if len(samples.shape) != 2:
        raise HTTPException(status_code=400, detail="Samples must be a 2D list.")
    if samples.shape[1] != 11:
        raise HTTPException(status_code=400, detail="Each sample must contain 11 features.")
    if samples.shape[0] != 5:
        raise HTTPException(status_code=400, detail="Exactly 5 samples required for verification.")
    model, scaler = load_artifacts(request.room_id)
    samples_scaled = scaler.transform(samples)
    predictions = model.predict(samples_scaled)
    positive_count = np.sum(predictions == 1)
    if positive_count >= 3:
        decision = "ACCEPT"
    else:
        decision = "REJECT"
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

    rooms = [
        name for name in os.listdir(models_folder)
        if os.path.isdir(os.path.join(models_folder, name))
    ]

    return {"rooms": rooms}