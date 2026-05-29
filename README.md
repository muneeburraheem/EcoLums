# EcoLums

EcoLums is an Android app that helps LUMS students track and reduce their carbon footprint. Students log daily transport, energy, and waste activities to earn points and badges, compete in team challenges, and see their collective campus-wide impact.

---

## Features

**For students**
- Log transport (bus, bike, car, walk, train), energy use, and waste
- Personal dashboard with CO₂ charts and activity streaks
- Campus-wide impact view showing the university's collective footprint
- Achievement badges and a live leaderboard
- Join clubs and compete in green team challenges
- Read sustainability tips curated by admins

**For admins**
- Campus-wide impact dashboard with departmental breakdown and CSV export
- Set and track campus milestone goals with milestone celebration notifications
- Manage sustainability tips, badges, merch store, and calculation metrics
- User management and full audit log
- Anomaly detection — flags suspicious activity submissions automatically

**Machine learning (all on-device)**
- CO₂ estimation using a TensorFlow Lite neural network (R² = 0.92)
- Anomaly detection using a TFLite autoencoder (~3% false-positive rate)
- Federated learning — the CO₂ model improves over time from real user data without any raw data leaving the device

---

## Tech Stack

- **Android** — Java, Material Design 3, ViewPager2
- **Firebase** — Authentication, Firestore, Storage
- **TensorFlow Lite** — on-device ML inference
- **MPAndroidChart** — charts and visualisations
- **ZXing** — QR code generation for club invites

---

## Team

Built by five LUMS students as a Software Engineering course project, Spring 2026.

| Name | GitHub |
|------|--------|
| Abdul Ahad Jawad | [aahadj2](https://github.com/aahadj2) |
| Amna Imran Rana | [aamnaimran](https://github.com/aamnaimran) |
| Syeda Fizza Sakina | [Fizza-Sakina](https://github.com/Fizza-Sakina) |
| Momin Fareed | [noxayyyy](https://github.com/noxayyyy) |
| Muneeb Ur Raheem | [muneeburraheem](https://github.com/muneeburraheem) |

---

## Design

- [Figma Storyboard](https://www.figma.com/design/9NxJKp96om53LlCAwKaDSy/Storyboard-Revised?node-id=0-1&t=tcy7NFh5gHBIGKJv-1)
- [UML Diagram](https://github.com/user-attachments/files/26546353/uml-2.pdf)
