# Corkboard [![CI](https://github.com/paxleslie/capstone/actions/workflows/ci.yml/badge.svg)](https://github.com/paxleslie/capstone/actions/workflows/ci.yml)

A household management app for families and roommates built to run on Android. With an emphasis on ease of use and intuitive controls, it centralizes tasks, chores and notes into a shared household board. Analogous to the real life inspiration, the emphasis on ease of use allows for those of limited technical experience to successfully interact with the app. A virtual alternative in a world that is rapidly adopting screens in communal areas.

With a built in household messaging system, households can stay connected in one centralized application. Completing chores earns points that can be redeemed in a rewards shop, promoting engagement and allowing for custom incentives for members of the household.

---

## Tech Stack

- **Android**: Kotlin, Jetpack Compose, MVVM
- **Backend**: Supabase (PostgreSQL, PostgREST, Auth)
- **Security**: Row Level Security (RLS) enforced at the database level
- **CI/CD**: GitHub Actions

---

## Architecture

![Data Flow Diagram](docs/Data%20Flow%20Diagram%20CORKBOARD%20APP.png)

The app follows a standard MVVM pattern:

- **UI Layer**: Jetpack Compose screens observe state via `StateFlow`
- **ViewModel Layer**: Handles business logic and exposes state to the UI
- **Repository Layer**: Isolated Supabase calls. One repository per domain
- **Supabase**: Authentication via JWT, data access via REST + PostgREST, Row Level Security ensures users can only access households they belong to

---

## Database Schema

![Database Schema](docs/Corkboard-DB.png)

Key relationships:
- A **User** can belong to multiple **Households** through **HouseholdMember**, which also tracks their role (admin/member) and points
- **Posts** belong to a Household and can be notes or chores
- **Rewards** belong to a Household and are tracked per member through **MemberRewards**

---

## Features

### Board
- Create, edit, and delete notes and chores
- Complete chores to earn points
- Filter posts by household
- Customize posts, completion stickers using rewards earned from the shop
<img width="1375" height="811" alt="Screenshot 2026-05-03 212122" src="https://github.com/user-attachments/assets/1ad79f75-6a42-4392-960c-49a4bc398fd7" />

### Shop
- Spend points to unlock rewards (post themes, completion stickers)
- Ownership is tracked per household member
<img width="417" height="857" alt="Screenshot 2026-05-03 212045" src="https://github.com/user-attachments/assets/d4b183c7-13b8-4cb3-91d1-eeb3ea1c116f" />

### Household Management
- Create and join households
- Add and remove members by email
- Admin role with elevated permissions (delete any post, edit member points)
- Rename households
<img width="387" height="855" alt="Screenshot 2026-05-03 214317" src="https://github.com/user-attachments/assets/bab50c4c-7127-4e00-86f1-1f0f4ea31e71" />

### Profile & Account
- Update display name, email, and password
- View your households and membership details
<img width="382" height="851" alt="Screenshot 2026-05-03 214355" src="https://github.com/user-attachments/assets/ee88176e-ecad-4847-bd23-2da5ab723a7f" />

### Messaging
- Messaging between household members
<img width="384" height="850" alt="Screenshot 2026-05-03 214517" src="https://github.com/user-attachments/assets/18fd7030-5368-4e3a-a3f8-73f2fec6b3a8" />

---

## CI/CD

GitHub Actions runs on every pull request to `main`:

1. **Unit tests**: ViewModel state logic for post CRUD operations (MockK + coroutines-test)
2. **Debug APK build**: Verifies the app compiles cleanly
3. **PLANNED - APK artifact**: Uploaded to the workflow run for download

---

## Installation

1. Download the APK from the latest [GitHub Actions](https://github.com/paxleslie/capstone/actions) run under **Artifacts**
2. On your Android device, enable **Install from unknown sources** (Settings → Security)
3. Open the downloaded APK and install
4. Sign up or log in to get started

---

## Team

Thomas Bakaysa, Pax Leslie, Scotty Roberts, Brandon Sanchez, Chyler Jones
