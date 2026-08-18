import json
import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score

print("=======================================")
print("🛡️ Training Attack Pattern (Intent) Classifier")
print("=======================================")

# 1. Load Data
dataset_path = "training/attack_patterns.json"
print(f"Loading synthetic dataset from {dataset_path}...")
with open(dataset_path, 'r') as f:
    data = json.load(f)

texts = [item['text'] for item in data]
labels = [item['label'] for item in data]

# 2. Split Data
X_train, X_test, y_train, y_test = train_test_split(texts, labels, test_size=0.2, random_state=42)
print(f"Train samples: {len(X_train)} | Test samples: {len(X_test)}")

# 3. Vectorization (TF-IDF)
print("\nVectorizing text data (TF-IDF)...")
vectorizer = TfidfVectorizer(max_features=1000, stop_words='english')
X_train_vec = vectorizer.fit_transform(X_train)
X_test_vec = vectorizer.transform(X_test)

# 4. Train Model
print("\nTraining Random Forest Multi-Class Classifier...")
classifier = RandomForestClassifier(n_estimators=100, random_state=42)
classifier.fit(X_train_vec, y_train)

# 5. Evaluate
print("\nEvaluating Model on Test Data...")
predictions = classifier.predict(X_test_vec)
acc = accuracy_score(y_test, predictions)
print(f"\n✅ Attack Pattern Accuracy: {acc*100:.2f}%\n")
print("Classification Report:")
print(classification_report(y_test, predictions))

# 6. Save Model
print("\nSaving model and vectorizer...")
joblib.dump(classifier, "attack_model.joblib")
joblib.dump(vectorizer, "attack_vectorizer.joblib")

print("✅ Saved 'attack_model.joblib' and 'attack_vectorizer.joblib'")
print("\nTest it! Try running inference manually if needed.")
