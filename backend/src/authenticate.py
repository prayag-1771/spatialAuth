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
    try:
        model, scaler = load_artifacts(room_id)
        samples = np.array(samples)
        
        if len(samples.shape) != 2:
            raise ValueError("Input must be a 2D list of samples.")
        if samples.shape[1] != 11:
            raise ValueError("Each sample must contain exactly 11 features.")
        if samples.shape[0] != 5:
            raise ValueError("Exactly 5 samples are required for verification.")
            
        # Scale the incoming samples using the scaler from enrollment
        samples_scaled = scaler.transform(samples)
        
        # Predict: 1 for inlier (match), -1 for outlier (no match)
        predictions = model.predict(samples_scaled)
        positive_count = np.sum(predictions == 1)
        
        # Use 3/5 majority vote for acceptance
        result = "ACCEPT" if positive_count >= 3 else "REJECT"
        
        print(f"--- Auth Report for {room_id} ---")
        print(f"Predictions: {predictions}")
        print(f"Positive count: {positive_count}/5")
        print(f"Decision: {result}")
        print("-------------------------------")
        
        return result
    except Exception as e:
        print(f"Authentication Error: {str(e)}")
        return "REJECT"
