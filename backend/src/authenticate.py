import numpy as np
import joblib
import os
def load_artifacts(room_id):
    room_folder = os.path.join("models", room_id)
    model_path = os.path.join(room_folder, "model.pkl")
    scaler_path = os.path.join(room_folder, "scaler.pkl")
    if not os.path.exists(model_path) or not os.path.exists(scaler_path):
        raise ValueError(f"Room '{room_id}' not enrolled.")
    model = joblib.load(model_path)
    scaler = joblib.load(scaler_path)
    return model, scaler
def authenticate(room_id, samples):
    model, scaler = load_artifacts(room_id)
    samples = np.array(samples)
    if len(samples.shape) != 2:
        raise ValueError("Input must be a 2D list of samples.")
    if samples.shape[1] != 11:
        raise ValueError("Each sample must contain exactly 11 features.")
    if samples.shape[0] != 5:
        raise ValueError("Exactly 5 samples are required for verification.")
    samples_scaled = scaler.transform(samples)
    predictions = model.predict(samples_scaled)
    positive_count = np.sum(predictions == 1)
    if positive_count >= 3:
        result = "ACCEPT"
    else:
        result = "REJECT"
    print("Predictions:", predictions)
    print("Positive count:", positive_count)
    print("Final Decision:", result)
    return result