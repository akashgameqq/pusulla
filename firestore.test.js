const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const pastDate = () => new Date(Date.now() - 5000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write scheduleItems", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("scheduleItems").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("scheduleItems").doc("item_1").set({
      userId: ALICE_UID,
      title: "Test",
    })
  );
});

test("Authenticated user can create, update, and list own scheduleItems", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ref = aliceDb.collection("users").doc(ALICE_UID).collection("scheduleItems").doc("item_1");
  await assertSucceeds(
    ref.set({
      userId: ALICE_UID,
      title: "Sabah Kosusu",
      notes: "5 km tempo",
      dateKey: "2026-11-15",
      startTime: "07:30",
      durationMinutes: 45,
      category: "Saglik",
      priority: "Yuksek",
      isCompleted: false,
      completedAtEpochMs: 0,
      repeatRule: "Her Gun",
      reminderMinutesBefore: 15,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(
    ref.update({
      isCompleted: true,
      completedAtEpochMs: 1763200000000,
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scheduleItems").get()
  );
});

test("Cross-user access to Bob's scheduleItems and mealLogs is denied", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("mealLogs").doc("meal_1").set({
      userId: BOB_UID,
      foodName: "Tavuk Salata",
      mealType: "Ogle",
      calories: 480,
      proteinG: 42.5,
      carbsG: 24.0,
      fatG: 18.0,
      portionLabel: "1 Porsiyon",
      sourceEngine: "Gemini AI",
      confidencePercent: 92,
      dateKey: "2026-11-15",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("mealLogs").doc("meal_1").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("mealLogs").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("weightLogs").doc("w_1").set({
      userId: ALICE_UID,
      weightKg: 70.0,
      dateKey: "2026-11-15",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Shadow fields and invalid payloads are rejected on user profile and weightLogs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Shadow field isAdmin rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      dailyCalorieGoal: 2200,
      dailyWaterGoalMl: 2500,
      targetWeightKg: 65.0,
      currentWeightKg: 68.5,
      heightCm: 170,
      isAdmin: true,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Valid user profile succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      dailyCalorieGoal: 2200,
      dailyWaterGoalMl: 2500,
      targetWeightKg: 65.0,
      currentWeightKg: 68.5,
      heightCm: 170,
      focusGoal: "Dengeli yasam",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Valid dailyWellness succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("dailyWellness").doc("2026-11-15").set({
      userId: ALICE_UID,
      dateKey: "2026-11-15",
      waterMl: 1500,
      focusMinutes: 50,
      moodScore: 4,
      energyScore: 5,
      reflectionNote: "Verimli bir gundu.",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Invalid dateKey document ID rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("dailyWellness").doc("invalid-date").set({
      userId: ALICE_UID,
      dateKey: "invalid-date",
      waterMl: 1000,
      focusMinutes: 25,
      moodScore: 4,
      energyScore: 4,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});
