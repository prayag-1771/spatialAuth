import pandas as pd
import numpy as np
import joblib
from sklearn.preprocessing import StandardScaler
from sklearn.svm import OneClassSVM
def load_enrollment_data(path):
    df = pd.read_csv(path)
    X = df.values
    return X
def train_one_class_svm(X):
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)
    model = OneClassSVM(
        kernel='rbf',
        gamma='scale',
        nu=0.1
    )
    model.fit(X_scaled)
    return model, scaler
def save_artifacts(model, scaler):
    joblib.dump(model, "models/room_model.pkl")
    joblib.dump(scaler, "models/scaler.pkl")
if __name__ == "__main__":
    data_path = "data/enrollment.csv"
    X = load_enrollment_data(data_path)
    print(f"Loaded {X.shape[0]} samples with {X.shape[1]} features.")
    model, scaler = train_one_class_svm(X)
    save_artifacts(model, scaler)
    print("Enrollment complete. Model saved.")