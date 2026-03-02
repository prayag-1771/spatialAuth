from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import numpy as np
import joblib
import os
from sklearn.preprocessing import StandardScaler
from sklearn.svm import OneClassSVM
app = FastAPI()
MODEL_PATH = "models/room_model.pkl"
SCALER_PATH = "models/scaler.pkl"
THRESHOLD = -0.2  
class EnrollmentRequest(BaseModel):
    samples: list[list[float]]
class AuthRequest(BaseModel):
    features: list[float]
def save_artifacts(model, scaler):
    joblib.dump(model, MODEL_PATH)
    joblib.dump(scaler, SCALER_PATH)
def load_artifacts():
    if not os.path.exists(MODEL_PATH) or not os.path.exists(SCALER_PATH):
        raise HTTPException(status_code=400, detail="Model not enrolled yet.")

    model = joblib.load(MODEL_PATH)
    scaler = joblib.load(SCALER_PATH)
    return model, scaler
@app.get("/health")
def health():
    return {"status": "API running"}
@app.post("/enroll")
def enroll(request: EnrollmentRequest):
    samples = np.array(request.samples)
    if samples.shape[1] != 11:
        raise HTTPException(status_code=400, detail="Each sample must have 11 features.")
    if samples.shape[0] < 20:
        raise HTTPException(status_code=400, detail="Minimum 20 samples required.")
    scaler = StandardScaler()
    samples_scaled = scaler.fit_transform(samples)
    model = OneClassSVM(
        kernel="rbf",
        gamma="scale",
        nu=0.1
    )
    model.fit(samples_scaled)
    save_artifacts(model, scaler)
    return {"message": "Enrollment successful", "samples_used": samples.shape[0]}
@app.post("/authenticate")
def authenticate(request: AuthRequest):
    if len(request.features) != 11:
        raise HTTPException(status_code=400, detail="Feature vector must contain 11 values.")
    model, scaler = load_artifacts()
    sample = np.array(request.features).reshape(1, -1)
    sample_scaled = scaler.transform(sample)
    score = model.decision_function(sample_scaled)[0]
    if score > THRESHOLD:
        decision = "ACCEPT"
    else:
        decision = "REJECT"
    return {
        "decision": decision,
        "score": float(score)
    }