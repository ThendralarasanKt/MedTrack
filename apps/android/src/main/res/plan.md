# 🏥 MedTrack — Offline Doctor Patient Management App
## Complete Project Plan for Android Studio with Claude

---

## 📋 Table of Contents
1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Project Structure](#3-project-structure)
4. [Database Schema](#4-database-schema)
5. [Screen & Navigation Map](#5-screen--navigation-map)
6. [Module Breakdown](#6-module-breakdown)
7. [Step-by-Step Build Order](#7-step-by-step-build-order)
8. [Claude Prompting Guide](#8-claude-prompting-guide)
9. [File Storage Strategy](#9-file-storage-strategy)
10. [Local Notifications Setup](#10-local-notifications-setup)
11. [Testing Checklist](#11-testing-checklist)

---

## 1. Project Overview

**App Name:** MedTrack (or your preferred name)
**Platform:** Android (minSdk 26 / Android 8.0+)
**Storage:** 100% local — Android Room (SQLite) + Scoped Internal Storage
**Users:** Single doctor, operates fully offline
**Purpose:** Manage patient records, visits, task assignments, reports, and follow-ups without any internet or cloud dependency.

### Core Features
- Digital patient records stored on device
- Visit-by-visit consultation logging with date/time stamps
- Task assignment to nurses and lab technicians (manual workflow)
- Photo/PDF report capture and storage (linked per visit)
- Medicine & prescription tracking
- Follow-up scheduling with local push notifications

---

## 2. Tech Stack

| Layer | Technology | Reason |
|---|---|---|
| Language | **Kotlin** | Modern Android standard |
| UI | **Jetpack Compose** | Declarative, fast to build |
| Architecture | **MVVM + Clean Architecture** | Separation of concerns, testable |
| Local DB | **Room (SQLite)** | Structured relational data, offline |
| File Storage | **Android Scoped Storage (Internal)** | Secure, no permissions needed |
| Navigation | **Navigation Compose** | Single-activity, multi-screen |
| Notifications | **WorkManager + NotificationManager** | Local follow-up reminders |
| Image Capture | **CameraX** | Capture lab reports via camera |
| File Picker | **ActivityResultContracts** | Pick PDFs/images from gallery |
| DI | **Hilt (Dagger)** | Dependency injection |
| Date/Time | **java.time (LocalDate, LocalDateTime)** | API 26+ |
| Build | **Gradle (Kotlin DSL)** | Type-safe build scripts |

---

## 3. Project Structure

```
app/
├── src/main/
│   ├── java/com/yourname/medtrack/
│   │   ├── data/
│   │   │   ├── db/
│   │   │   │   ├── AppDatabase.kt            ← Room database instance
│   │   │   │   ├── dao/
│   │   │   │   │   ├── PatientDao.kt
│   │   │   │   │   ├── VisitDao.kt
│   │   │   │   │   ├── TaskDao.kt
│   │   │   │   │   ├── ReportDao.kt
│   │   │   │   │   ├── MedicineDao.kt
│   │   │   │   │   └── FollowUpDao.kt
│   │   │   │   └── entity/
│   │   │   │       ├── PatientEntity.kt
│   │   │   │       ├── VisitEntity.kt
│   │   │   │       ├── TaskEntity.kt
│   │   │   │       ├── ReportEntity.kt
│   │   │   │       ├── MedicineEntity.kt
│   │   │   │       └── FollowUpEntity.kt
│   │   │   ├── repository/
│   │   │   │   ├── PatientRepository.kt
│   │   │   │   ├── VisitRepository.kt
│   │   │   │   └── FollowUpRepository.kt
│   │   │   └── storage/
│   │   │       └── FileStorageManager.kt     ← Handles image/PDF saves
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   │   ├── Patient.kt
│   │   │   │   ├── Visit.kt
│   │   │   │   ├── Task.kt
│   │   │   │   ├── Report.kt
│   │   │   │   ├── Medicine.kt
│   │   │   │   └── FollowUp.kt
│   │   │   └── usecase/
│   │   │       ├── AddPatientUseCase.kt
│   │   │       ├── GetPatientListUseCase.kt
│   │   │       ├── AddVisitUseCase.kt
│   │   │       ├── UpdateTaskStatusUseCase.kt
│   │   │       └── ScheduleFollowUpUseCase.kt
│   │   ├── ui/
│   │   │   ├── MainActivity.kt               ← Single Activity
│   │   │   ├── navigation/
│   │   │   │   └── AppNavGraph.kt            ← All routes defined here
│   │   │   ├── dashboard/
│   │   │   │   ├── DashboardScreen.kt
│   │   │   │   └── DashboardViewModel.kt
│   │   │   ├── patient/
│   │   │   │   ├── AddPatientScreen.kt
│   │   │   │   ├── PatientProfileScreen.kt
│   │   │   │   └── PatientViewModel.kt
│   │   │   ├── visit/
│   │   │   │   ├── NewVisitScreen.kt
│   │   │   │   ├── VisitDetailScreen.kt
│   │   │   │   └── VisitViewModel.kt
│   │   │   ├── task/
│   │   │   │   ├── TaskSection.kt            ← Composable inside Visit
│   │   │   │   └── TaskViewModel.kt
│   │   │   ├── report/
│   │   │   │   ├── ReportSection.kt          ← Composable inside Visit
│   │   │   │   └── ReportViewModel.kt
│   │   │   ├── followup/
│   │   │   │   ├── FollowUpScreen.kt         ← Global follow-up tab
│   │   │   │   └── FollowUpViewModel.kt
│   │   │   └── common/
│   │   │       ├── components/               ← Reusable Composables
│   │   │       └── theme/
│   │   │           ├── Color.kt
│   │   │           ├── Theme.kt
│   │   │           └── Type.kt
│   │   ├── notification/
│   │   │   ├── FollowUpNotificationWorker.kt ← WorkManager Worker
│   │   │   └── NotificationHelper.kt
│   │   └── di/
│   │       ├── AppModule.kt                  ← Hilt modules
│   │       └── DatabaseModule.kt
│   ├── res/
│   │   ├── drawable/
│   │   ├── values/
│   │   └── xml/
│   └── AndroidManifest.xml
└── build.gradle.kts
```

---

## 4. Database Schema

### 4.1 Entities and Relationships

```
Patient (1) ──────< Visit (many)
                      │
                      ├──< Task (many)
                      ├──< Report (many)
                      ├──< Medicine (many)
                      └──< FollowUp (many, also linked to Patient)
```

### 4.2 Table Definitions

#### `patients` Table
```sql
CREATE TABLE patients (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    age         INTEGER NOT NULL,
    sex         TEXT NOT NULL,            -- "Male" | "Female" | "Other"
    contact     TEXT,
    address     TEXT,
    medHistory  TEXT,                     -- Free text: previous conditions
    createdAt   TEXT NOT NULL             -- ISO-8601 timestamp
);
```

#### `visits` Table
```sql
CREATE TABLE visits (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    patientId   INTEGER NOT NULL,
    visitDate   TEXT NOT NULL,            -- "2026-05-04"
    visitTime   TEXT NOT NULL,            -- "14:51"
    symptoms    TEXT,
    diagnosis   TEXT,
    progressNotes TEXT,
    FOREIGN KEY (patientId) REFERENCES patients(id) ON DELETE CASCADE
);
```

#### `tasks` Table
```sql
CREATE TABLE tasks (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    visitId         INTEGER NOT NULL,
    taskName        TEXT NOT NULL,        -- e.g., "Complete Blood Count"
    assignedTo      TEXT NOT NULL,        -- e.g., "Nurse Sarah"
    role            TEXT NOT NULL,        -- "Nurse" | "Lab Technician"
    instructions    TEXT,
    status          TEXT NOT NULL DEFAULT 'PENDING',  -- "PENDING" | "DONE"
    FOREIGN KEY (visitId) REFERENCES visits(id) ON DELETE CASCADE
);
```

#### `reports` Table
```sql
CREATE TABLE reports (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    visitId     INTEGER NOT NULL,
    taskId      INTEGER,                  -- Optional: link report to specific task
    fileName    TEXT NOT NULL,
    filePath    TEXT NOT NULL,            -- Internal storage path
    fileType    TEXT NOT NULL,            -- "IMAGE" | "PDF"
    uploadedAt  TEXT NOT NULL,
    FOREIGN KEY (visitId) REFERENCES visits(id) ON DELETE CASCADE
);
```

#### `medicines` Table
```sql
CREATE TABLE medicines (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    visitId     INTEGER NOT NULL,
    name        TEXT NOT NULL,
    dosage      TEXT,                     -- e.g., "500mg twice daily"
    duration    TEXT,                     -- e.g., "7 days"
    notes       TEXT,
    FOREIGN KEY (visitId) REFERENCES visits(id) ON DELETE CASCADE
);
```

#### `follow_ups` Table
```sql
CREATE TABLE follow_ups (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    patientId           INTEGER NOT NULL,
    visitId             INTEGER NOT NULL,
    scheduledDate       TEXT NOT NULL,    -- "2026-05-18"
    reason              TEXT,
    isNotified          INTEGER DEFAULT 0,   -- 0=false, 1=true
    workManagerId       TEXT,             -- UUID from WorkManager job
    FOREIGN KEY (patientId) REFERENCES patients(id) ON DELETE CASCADE,
    FOREIGN KEY (visitId) REFERENCES visits(id) ON DELETE CASCADE
);
```

### 4.3 Room Kotlin Entity Example

```kotlin
// PatientEntity.kt
@Entity(tableName = "patients")
data class PatientEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val age: Int,
    val sex: String,
    val contact: String = "",
    val address: String = "",
    val medHistory: String = "",
    val createdAt: String = LocalDateTime.now().toString()
)
```

---

## 5. Screen & Navigation Map

```
AppNavGraph
│
├── ROUTE: "dashboard"           ← DashboardScreen (Start Destination)
│   └── Bottom Nav Tabs:
│       ├── [Patients]           ← Patient list + search
│       └── [Follow-Ups]         ← FollowUpScreen (global list)
│
├── ROUTE: "add_patient"         ← AddPatientScreen (modal/full screen)
│
├── ROUTE: "patient/{patientId}" ← PatientProfileScreen
│   └── Tabs inside:
│       ├── [Basic Info]         ← Static patient details
│       ├── [Visits]             ← Chronological list of visits
│       └── [Diagnosis]          ← History across all visits
│
├── ROUTE: "new_visit/{patientId}"     ← NewVisitScreen
│   └── Sections (scrollable):
│       ├── Date & Time (auto)
│       ├── Symptoms
│       ├── Diagnosis
│       ├── Tasks
│       ├── Medicines
│       ├── Progress Notes
│       └── Follow-Up Picker
│
└── ROUTE: "visit_detail/{visitId}"   ← VisitDetailScreen (read + edit)
    └── Sections:
        ├── Visit Summary
        ├── Tasks (with status toggle)
        ├── Reports (upload + view)
        └── Medicines
```

---

## 6. Module Breakdown

### Module A — Doctor Dashboard
**Screen:** `DashboardScreen.kt`

Functionality:
- Bottom navigation with 2 tabs: **Patients** and **Follow-Ups**
- **Patients Tab:** Scrollable list of all patients, search bar at top
- FAB (Floating Action Button) `[+ Add Patient]`
- Each patient card shows: Name, Age, last visit date
- Tap patient → navigate to `PatientProfileScreen`

ViewModel responsibilities:
- `getAllPatients(): Flow<List<Patient>>`
- `searchPatients(query: String): Flow<List<Patient>>`

---

### Module B — Add Patient
**Screen:** `AddPatientScreen.kt`

Form fields:
- Full Name (required)
- Age (required, numeric)
- Sex (dropdown: Male / Female / Other)
- Contact Number
- Address
- Previous Medical History (multiline text)

On Save:
- Validate required fields
- Insert into `patients` table via `PatientRepository`
- Navigate back to Dashboard

---

### Module C — Patient Profile
**Screen:** `PatientProfileScreen.kt`

Tabs:
1. **Basic Info Tab** — Display all registration fields (read-only with edit option)
2. **Visits Tab** — Chronological list of visits (newest first)
    - Each visit card: Date, Time, brief symptom summary
    - Button: `[+ New Visit]` → navigates to `NewVisitScreen`
    - Tap visit → `VisitDetailScreen`
3. **Diagnosis Tab** — Aggregated diagnosis history across all visits

---

### Module D — New Visit / Visit Detail
**Screen:** `NewVisitScreen.kt` / `VisitDetailScreen.kt`

**Section 1 — Visit Header**
- Date: auto-filled from system (`LocalDate.now()`)
- Time: auto-filled from system (`LocalTime.now()`)

**Section 2 — Consultation**
- Problem / Symptoms (multiline text field)
- First Diagnosis (multiline text field)
- Progress Notes (multiline text field)

**Section 3 — Task Assignment**
- Button: `[+ Add Task]`
- Task entry dialog:
    - Task Name (e.g., "Blood Test")
    - Assigned To (text field: person's name)
    - Role (dropdown: Nurse / Lab Technician)
    - Instructions (optional multiline)
- Each task shows: name, assignee, status chip [PENDING / DONE]
- Tapping status chip toggles it (PENDING → DONE)

**Section 4 — Reports**
- Button: `[Upload Report]`
- Opens bottom sheet with options: Camera / Gallery / File
- Captured file saved to internal storage
- Thumbnail grid of uploaded reports
- Tap report → full-screen viewer

**Section 5 — Medicines Prescribed**
- Button: `[+ Add Medicine]`
- Medicine entry: Name, Dosage, Duration, Notes
- Listed below button

**Section 6 — Follow-Up**
- Button: `[+ Schedule Follow-Up]`
- Date picker dialog
- Optional reason text field
- On save: creates `FollowUpEntity` + schedules WorkManager job

**Save Visit:**
- Saves `VisitEntity` first → gets visitId
- Saves all Tasks, Medicines, Reports with visitId FK
- Saves FollowUp with visitId + patientId FK

---

### Module E — Follow-Up Screen
**Screen:** `FollowUpScreen.kt`

**Data source:** Query all `follow_ups` JOIN `patients` ordered by `scheduledDate`

**Display:**
- Section headers: "Overdue", "Today", "Tomorrow", "This Week", "Later"
- Each follow-up card: Patient name, Date, Reason, Visit reference
- Tap card → navigates to that patient's profile

**Notification:**
- WorkManager fires at 8 AM on scheduled date
- Notification: "Follow-up today: [Patient Name]"
- Tapping notification → deep links to patient profile

---

## 7. Step-by-Step Build Order

Follow this exact sequence to avoid dependency issues.

### Phase 1 — Project Setup (Day 1)

**Step 1.1 — Create the Project**
- Open Android Studio → New Project → Empty Activity (Compose)
- Package: `com.yourname.medtrack`
- Min SDK: 26
- Language: Kotlin

**Step 1.2 — Add Dependencies**
Paste into `app/build.gradle.kts`:
```kotlin
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.51")
    kapt("com.google.dagger:hilt-compiler:2.51")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
}
```

**Step 1.3 — Configure AndroidManifest**
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

---

### Phase 2 — Database Layer (Day 1–2)

**Step 2.1** — Create all 6 Entity classes in `data/db/entity/`

**Step 2.2** — Create all 6 DAO interfaces in `data/db/dao/`
- Each DAO: `insert`, `update`, `delete`, `getAll`, `getById`, `getByForeignKey`
- Use `Flow<List<Entity>>` return types for reactive updates

**Step 2.3** — Create `AppDatabase.kt`
```kotlin
@Database(
    entities = [PatientEntity::class, VisitEntity::class, TaskEntity::class,
                ReportEntity::class, MedicineEntity::class, FollowUpEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun visitDao(): VisitDao
    abstract fun taskDao(): TaskDao
    abstract fun reportDao(): ReportDao
    abstract fun medicineDao(): MedicineDao
    abstract fun followUpDao(): FollowUpDao
}
```

**Step 2.4** — Create Repository classes in `data/repository/`

**Step 2.5** — Set up Hilt `DatabaseModule.kt`
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "medtrack.db").build()

    @Provides fun providePatientDao(db: AppDatabase) = db.patientDao()
    // ... repeat for each DAO
}
```

**Claude prompt for this phase:**
> "Generate all 6 Room Entity data classes and their DAO interfaces for my Android Kotlin app using this SQL schema: [paste schema from Section 4]. Use Flow for list queries. Add a PatientWithVisits data class using @Relation."

---

### Phase 3 — Domain Layer (Day 2)

- Create domain `model/` classes (clean Kotlin data classes, no Room annotations)
- Create Use Case classes — each wraps one repository call
- Create mapper functions: `PatientEntity.toDomain()` and `Patient.toEntity()`

**Claude prompt:**
> "Create Kotlin mapper extension functions to convert between PatientEntity and Patient domain model. Then create AddPatientUseCase and GetPatientListUseCase using PatientRepository."

---

### Phase 4 — Navigation Setup (Day 2)

Create `AppNavGraph.kt`:
```kotlin
@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = "dashboard") {
        composable("dashboard") { DashboardScreen(navController) }
        composable("add_patient") { AddPatientScreen(navController) }
        composable("patient/{patientId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("patientId")?.toInt()
            PatientProfileScreen(navController, patientId = id!!)
        }
        composable("new_visit/{patientId}") { ... }
        composable("visit_detail/{visitId}") { ... }
    }
}
```

---

### Phase 5 — UI Screens (Day 3–6)

Build screens in this order:
1. `DashboardScreen` with patient list (hardcoded data first)
2. `AddPatientScreen` with form + validation
3. `PatientProfileScreen` with tab layout
4. `NewVisitScreen` (most complex — build section by section)
5. `VisitDetailScreen` with task status toggle
6. `FollowUpScreen` with grouped list

**Claude prompt for each screen:**
> "Build a Jetpack Compose screen for [DashboardScreen]. It should display a LazyColumn of PatientCard composables from a list in the ViewModel. Include a TopAppBar with title 'My Patients' and a FloatingActionButton with a plus icon. Use Material3 components."

---

### Phase 6 — File Storage (Day 6)

Create `FileStorageManager.kt`:
```kotlin
class FileStorageManager(private val context: Context) {

    fun saveImageToStorage(uri: Uri, visitId: Int): String {
        val dir = File(context.filesDir, "reports/visit_$visitId")
        dir.mkdirs()
        val fileName = "report_${System.currentTimeMillis()}.jpg"
        val destFile = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return destFile.absolutePath  // Store this path in DB
    }

    fun getImageFile(path: String): File = File(path)

    fun deleteFile(path: String) { File(path).delete() }
}
```

---

### Phase 7 — Notifications (Day 7)

**Step 7.1** — Create `NotificationHelper.kt`
```kotlin
fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        "follow_up_channel",
        "Follow-Up Reminders",
        NotificationManager.IMPORTANCE_HIGH
    )
    context.getSystemService(NotificationManager::class.java)
        .createNotificationChannel(channel)
}
```

**Step 7.2** — Create `FollowUpNotificationWorker.kt`
```kotlin
class FollowUpNotificationWorker(
    ctx: Context, params: WorkerParameters
) : Worker(ctx, params) {
    override fun doWork(): Result {
        val patientName = inputData.getString("patient_name") ?: return Result.failure()
        val patientId = inputData.getInt("patient_id", -1)
        // Build and show notification with deep link to patient profile
        return Result.success()
    }
}
```

**Step 7.3** — Schedule in `ScheduleFollowUpUseCase.kt`
```kotlin
val delay = ChronoUnit.MILLIS.between(LocalDateTime.now(), scheduledDateTime)
val request = OneTimeWorkRequestBuilder<FollowUpNotificationWorker>()
    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
    .setInputData(workDataOf("patient_name" to name, "patient_id" to id))
    .build()
WorkManager.getInstance(context).enqueue(request)
```

---

### Phase 8 — Polish & Testing (Day 8)

- Add empty state illustrations
- Add loading states with `CircularProgressIndicator`
- Add confirmation dialogs for delete actions
- Test all navigation flows
- Test database operations with sample data
- Test notification delivery

---

## 8. Claude Prompting Guide

Use these prompts in Claude Code or Claude chat to generate specific pieces:

### Getting the full Room setup:
> "Generate a complete Android Room database setup in Kotlin for a medical app. I need 6 entities: Patient, Visit, Task, Report, Medicine, FollowUp. Include @ForeignKey constraints, index fields, all DAO interfaces with Flow return types, and the @Database class. Follow this schema: [paste Section 4.2]"

### Generating a Compose screen:
> "Build the NewVisitScreen in Jetpack Compose (Material3) for an Android medical app. The screen is a scrollable Column with these sections: 1) Auto-filled date and time display, 2) TextField for symptoms, 3) TextField for diagnosis, 4) A Task section that shows added tasks in a LazyColumn and has an 'Add Task' button that opens an AlertDialog form, 5) A medicine section, 6) A follow-up date picker. Use a ViewModel with StateFlow. The screen title in TopAppBar should be 'New Visit'."

### Generating a ViewModel:
> "Create a VisitViewModel in Kotlin using Hilt injection. It should expose: visitState as StateFlow<VisitUiState>, tasks as StateFlow<List<Task>>, medicines as StateFlow<List<Medicine>>. Include functions: saveVisit(), addTask(task: Task), toggleTaskStatus(taskId: Int), addMedicine(medicine: Medicine), scheduleFollowUp(date: LocalDate). Use viewModelScope for coroutines."

### Generating file upload logic:
> "Write a Kotlin composable function ReportUploadSection that lets the user pick an image from gallery or take a photo with CameraX. Use ActivityResultContracts.TakePicture and ActivityResultContracts.GetContent. On selection, call viewModel.saveReport(uri). Display picked images in a 3-column LazyVerticalGrid with Coil's AsyncImage."

### Generating notification scheduling:
> "Write a WorkManager OneTimeWorkRequest setup in Kotlin that fires a notification on a specific future date at 8 AM. Accept patientId and patientName as input data. The notification should have a tap action that deep-links to 'patient/{patientId}' route in NavController."

---

## 9. File Storage Strategy

```
Internal Storage (/data/data/com.yourname.medtrack/)
└── files/
    └── reports/
        ├── visit_1/
        │   ├── report_1714832400000.jpg
        │   └── report_1714832500000.jpg
        ├── visit_2/
        │   └── report_1714900000000.jpg
        └── visit_5/
            └── report_1715000000000.jpg
```

**Rules:**
- Never store binary data in SQLite — store only the file path string
- Files are in app's private internal storage — no READ_EXTERNAL_STORAGE needed on Android 10+
- When a Visit is deleted, cascade-delete its DB rows AND call `FileStorageManager.deleteFile()` for each linked report
- When displaying: load path from DB → pass to `AsyncImage(model = File(path))` via Coil

---

## 10. Local Notifications Setup

### Flow:
1. Doctor adds follow-up date in `NewVisitScreen`
2. `ScheduleFollowUpUseCase` saves `FollowUpEntity` to DB
3. Same use case enqueues a `OneTimeWorkRequest` with delay = (scheduled date 8 AM) - now
4. WorkManager fires Worker on that date
5. Worker builds a `NotificationCompat` notification
6. Doctor taps notification → app opens → navigates to patient profile

### Important:
- Request `POST_NOTIFICATIONS` permission at runtime on Android 13+
- Store WorkManager request UUID in `follow_ups.workManagerId` column (allows cancellation if follow-up is deleted)
- To cancel: `WorkManager.getInstance(ctx).cancelWorkById(UUID.fromString(workManagerId))`

---

## 11. Testing Checklist

### Database Tests
- [ ] Insert patient → appears in dashboard list
- [ ] Add visit → appears in patient profile visits tab
- [ ] Add task → appears in visit detail with PENDING status
- [ ] Toggle task to DONE → persists after app restart
- [ ] Upload report photo → thumbnail appears; file exists at stored path
- [ ] Delete patient → all child visits, tasks, reports, follow-ups cascade deleted
- [ ] Schedule follow-up → appears in Follow-Ups tab

### Navigation Tests
- [ ] Dashboard → Add Patient → save → back to Dashboard with new patient
- [ ] Dashboard → Patient Profile → New Visit → save → visit appears in profile
- [ ] Follow-Up tab shows follow-up; tapping navigates to patient

### Notification Tests
- [ ] Schedule a follow-up for tomorrow → set device clock forward → notification appears
- [ ] Tap notification → opens correct patient profile
- [ ] Delete follow-up → notification does not fire (WorkManager job cancelled)

### Edge Cases
- [ ] Search with partial patient name works
- [ ] Empty dashboard shows "No patients yet" empty state
- [ ] Patient with zero visits shows empty visits list
- [ ] Camera permission denied → graceful fallback message

---

## 12. Detailed Workflow Summary

```
INSTALL APP
    │
    ▼
DASHBOARD (Patient List)
    │
    ├──[+ Add Patient]──────────────────────────────────────┐
    │                                                       │
    │   Fill form: Name, Age, Sex, Contact, Address,        │
    │   Medical History → [Save] → Back to Dashboard        │
    │                                                       │
    ◄──────────────────────────────────────────────────────┘
    │
    ├──[Tap Patient]────────────────────────────────────────┐
    │                                                       │
    │   PATIENT PROFILE                                     │
    │   ├── Basic Info Tab (read-only, edit button)         │
    │   ├── Visits Tab                                      │
    │   │       │                                           │
    │   │       ├──[+ New Visit]─────────────────────────┐  │
    │   │       │                                        │  │
    │   │       │   NEW VISIT SCREEN                     │  │
    │   │       │   ├── Auto Date & Time                 │  │
    │   │       │   ├── Symptoms (text)                  │  │
    │   │       │   ├── Diagnosis (text)                 │  │
    │   │       │   ├── Tasks                            │  │
    │   │       │   │   └──[+ Add Task]→ Dialog          │  │
    │   │       │   │       Name, Assignee, Role, Notes  │  │
    │   │       │   ├── Medicines                        │  │
    │   │       │   │   └──[+ Add Medicine]→ Dialog      │  │
    │   │       │   ├── Progress Notes                   │  │
    │   │       │   ├──[+ Schedule Follow-Up]→ DatePick  │  │
    │   │       │   └──[Save Visit]                      │  │
    │   │       │            │                           │  │
    │   │       │            ▼                           │  │
    │   │       │     Saved to Room DB                   │  │
    │   │       │     WorkManager job scheduled          │  │
    │   │       │                                        │  │
    │   │       ◄────────────────────────────────────────┘  │
    │   │                                                   │
    │   │       ├──[Tap Visit]──────────────────────────┐   │
    │   │                                               │   │
    │   │           VISIT DETAIL SCREEN                 │   │
    │   │           ├── Visit summary (read)            │   │
    │   │           ├── Tasks with [PENDING/DONE] toggle│   │
    │   │           ├── Reports                         │   │
    │   │           │   └──[Upload Report]              │   │
    │   │           │       Camera / Gallery → saved    │   │
    │   │           └── Medicines list                  │   │
    │   │                                               │   │
    │   ◄───────────────────────────────────────────────┘   │
    │                                                       │
    └───────────────────────────────────────────────────────┘
    │
    ├──[Follow-Up Tab]──────────────────────────────────────┐
    │                                                       │
    │   FOLLOW-UP SCREEN                                    │
    │   ├── Overdue section (red)                           │
    │   ├── Today section (highlighted)                     │
    │   ├── Tomorrow section                                │
    │   └── Later section                                   │
    │   [Tap item] → Patient Profile                        │
    │                                                       │
    └───────────────────────────────────────────────────────┘
    │
    NOTIFICATION (fires at 8 AM on follow-up date)
    "Follow-up today: [Patient Name]"
    [Tap] → Patient Profile
```

---

*Generated for Android Studio Kotlin + Jetpack Compose project. All data stored exclusively on device using Android Room (SQLite) and Internal Scoped Storage.*