# Presley

**QR and location based attendance for classrooms.**
Say present. Scan once.

Presley is an Android app (Java) built as a mini project for the Mobile Application Development Laboratory (CSD422), Maharashtra Institute of Technology, Chhatrapati Sambhajinagar. A teacher starts a session and shows a QR code that changes every 30 seconds. Students scan it with their own phones. Attendance is accepted only if the student belongs to the class, the code is the latest one, and the student is inside the classroom area.

Author: Nikita

---

## Why this project

Roll call takes five to ten minutes of every lecture, and one student can answer for another. Paper registers must be typed into Excel later. Presley makes attendance take seconds, makes proxy attendance harder, and produces the attendance sheets automatically.

## Features

### Teacher
- Create classes and share a six-character join code
- Start a session for a class, with a QR code that refreshes every 30 seconds
- Live count of students present while the session runs
- Location radius for the session (default 100 m, adjustable from 20 m to 500 m in Profile)
- Late marking: students who scan after a grace time (set per session) are marked Late
- Manual override: mark a student present with a reason, or remove a wrong entry
- Same-phone warning when two students mark attendance from one phone
- Reports: list of past sessions, present / absent / late for each lecture
- Students at risk: list of students below 75% for a class
- Export CSV sheets: one lecture, or a whole subject (one column per lecture, P / L / A, total and percentage)

### Student
- Join a class with the teacher's code
- Scan the QR code to mark attendance, with clear messages (QR expired, Already marked, Too far from class, Not in this class, Session ended)
- Attendance percentage for each subject with a progress bar, in red below the warning limit
- Skip advice, such as "You can skip 2 lectures and stay at or above 75%"
- Report saved on the phone (SQLite), so History still opens without internet
- Warning limit can be changed in Profile (default 75%)

### Both
- Signup and login with email and password, role based screens
- Edit profile, change password, About, logout with confirmation
- Material 3 design, bottom navigation with Fragments, splash screen

## How a scan is checked

1. The QR text is `sessionId:token`.
2. The session must exist and be active.
3. The token must match the current token in the database (a photo of an old QR fails).
4. The student must have joined the class of the session.
5. The student must not already be marked for this session.
6. The student's current location must be within the session radius of the teacher's location.
7. The record is saved with the server time, the distance in metres, and a shortened hash of the device id.

Lateness is decided by comparing two server timestamps (session start and scan time), so changing the phone clock has no effect.

## Privacy

- Location is read once, at the moment of marking. There is no background tracking.
- A student's coordinates are not stored. Only the distance in metres is saved.
- The classroom location is saved with the session.
- The device code is a shortened SHA-256 hash of the Android id, so it cannot be traced back to a phone owner.
- Only the minimum data is kept: name, roll number, subject, time, distance.

## Tech stack

| Part | Technology |
|---|---|
| Language and IDE | Java, XML, Android Studio |
| UI | Material 3 components, Fragments, BottomNavigationView |
| Login | Firebase Authentication (email and password) |
| Database | Cloud Firestore with security rules |
| Offline report | SQLite (SQLiteOpenHelper) |
| Settings | SharedPreferences |
| QR code | ZXing Android Embedded |
| Location | Fused Location Provider (Google Play services) |
| File export | Storage Access Framework (CSV) |

Minimum SDK 24.

## Project structure

```
app/src/main/java/com/smartattend/app/
  SplashActivity            routes to login or the right home screen
  LoginActivity             login, forgot password
  SignupActivity            signup with role (student / teacher)
  StudentHomeActivity       tabs: Scan, History, Profile
    StudentScanFragment     scan QR, all checks, save attendance
    StudentHistoryFragment  percentages, skip advice, offline mode
  TeacherHomeActivity       tabs: Home, Reports, Profile
    TeacherHomeFragment     pick a class, start a session
    TeacherReportsFragment  past sessions, subject sheet, students at risk
  ProfileFragment           profile, password, settings, About, logout
  TeacherSessionActivity    QR code, token refresh, live count
  SessionDetailActivity     present / absent / late, manual override, CSV
  ClassesActivity           create, share, join, leave classes
  DBHelper                  SQLite table for the offline report
  Ui                        screen edge handling for new Android versions
```

## Data model (Cloud Firestore)

| Collection | Document id | Main fields |
|---|---|---|
| users | Firebase user id | name, email, role, rollNo, classIds, classNames |
| classes | six-character class code | name, teacherId, code, createdAt |
| sessions | auto id | classId, subject, teacherId, active, currentToken, lat, lng, radius, lateAfterMinutes, createdAt |
| attendance | `sessionId_studentId` | sessionId, studentId, name, rollNo, subject, markedAt, distanceMeters, deviceId, and for manual records: manual, reason, markedBy |

Because the attendance id is `sessionId_studentId`, a student can have only one record per session.

## Run it yourself

The Firebase config file is not in this repository, so you need your own Firebase project.

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an Android app with the package name `com.smartattend.app`.
3. Download `google-services.json` and put it in the `app/` folder. Do not commit it.
4. In Authentication, enable the **Email/Password** sign-in method.
5. Create a **Firestore Database**.
6. Open Firestore > Rules, paste the contents of [`firestore.rules`](firestore.rules), and click Publish.
7. Open the project in Android Studio, let Gradle sync, and run it on a phone or an emulator with Google Play services.

To try the full flow you need two accounts: one teacher and one student. The teacher creates a class and shares the code, the student joins it, the teacher starts a session, and the student scans the QR.

Camera and location permissions are requested when needed. On an emulator, set a location first (Extended controls > Location).

## Build an APK

In Android Studio: **Build > Build Bundle(s) / APK(s) > Build APK(s)**, then open the folder with **locate**.

## Screenshots

Add your screenshots to a `screenshots/` folder and list them here.

| Teacher session | Student scan | Student history |
|---|---|---|
| `screenshots/teacher_session.png` | `screenshots/student_scan.png` | `screenshots/student_history.png` |

## Limitations

- Android only. Students without an Android phone have to be marked by the teacher using manual override.
- A QR code shown on one phone is hard to scan from the back of a large room. It works best on a projector or a casted screen, or in smaller groups.
- No software can fully stop proxy attendance. A friend who receives the code and is standing within the radius can still mark attendance, and a student can hand their logged-in phone to a friend. Presley makes this harder and visible (refreshing code, location radius, same-phone warning, live count the teacher can compare with a headcount).
- Marking attendance needs internet, because the token is checked against the server.
- Most checks run inside the app. The Firestore rules limit what a user can write, but a modified app could still skip some checks. Server-side validation with Cloud Functions would be the complete fix.
- The role (teacher or student) is chosen at signup. A real college would create teacher accounts through an administrator.
- GPS can be less accurate indoors, so keep the radius large enough for your classroom.

## Future scope

- A short announced code as an alternative to the QR, for rooms with no screen
- Binding each account to one phone, and rejecting simulated (fake) GPS locations
- A headcount check when the teacher ends a session
- A Bluetooth proximity token, so the session secret never leaves the room
- Face or fingerprint verification before marking
- Server-side validation with Cloud Functions
- Administrator login, notifications for students near the shortage limit, and an iOS version

## Course

Mobile Application Development Laboratory (CSD422), Final Year B.Tech (Computer Science and Design), Maharashtra Institute of Technology, Chhatrapati Sambhajinagar.
