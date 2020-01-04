import json
from sklearn import preprocessing
from sklearn.naive_bayes import GaussianNB
from sklearn.model_selection import train_test_split
from sklearn import metrics
import random


with open('soundtrack-for-life-songs-export.json', 'r', encoding='UTF-8') as myfile:
    data=myfile.read()

res = json.loads(data)

features = []
labels = []
activities = []
joined_dict = {**res['feedback'], **res['playlist']}

values = joined_dict.values()
random.shuffle(list(joined_dict.values()))

for i in values:
    if 'features' in i and 'activity' in i and 'feedback' in i:
        features.append(i['features'])
        if i['feedback'] == 'playlist':
            labels.append('like')
        else:
            labels.append(i['feedback'])
        activities.append(i['activity'])

le = preprocessing.LabelEncoder()
activities_encoded = le.fit_transform(activities)
labels_encoded = le.fit_transform(labels)

# combine all inputs (add activity to feature data)
for index, a in enumerate(activities_encoded):
    features[index].append(a)

X_train, X_test, y_train, y_test = train_test_split(features, labels_encoded, test_size=0.15, random_state=1)

gnb = GaussianNB()

gnb.fit(X_train, y_train)

y_pred = gnb.predict(X_test)

print("Accuracy:",metrics.accuracy_score(y_test, y_pred))
