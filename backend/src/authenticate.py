import numpy as np
import joblib


def load_artifacts():
    model = joblib.load("models/room_model.pkl")
    scaler = joblib.load("models/scaler.pkl")
    return model, scaler


def authenticate(sample):
    model, scaler = load_artifacts()

    sample = np.array(sample).reshape(1, -1)

    sample_scaled = scaler.transform(sample)

    prediction = model.predict(sample_scaled)
    decision_score = model.decision_function(sample_scaled)

    threshold = -0.2
    if decision_score[0] > threshold:
        print("ACCEPT")
    else:
        print("REJECT")

    print(f"Decision score: {decision_score[0]}")


if __name__ == "__main__":

    # Test sample (similar to enrollment)
    test_sample = [80,80,80,-30,-30,-30,50,50,50,50,50]

    authenticate(test_sample)