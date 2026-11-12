const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "ai-studio-applet-webapp-7e93f";
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

test("unauthenticated user cannot read user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.doc(`users/${ALICE_UID}`).get());
});

test("alice can create her own user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      createdAt: new Date(),
      updatedAt: new Date()
    })
  );
});

test("bob cannot read alice user profile", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}`).get());
});

test("alice can manage her vcam config", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/configs/default`).set({
      userId: ALICE_UID,
      isMasterEnabled: true,
      rotation: 0,
      isMirrored: false,
      isVerticalFlipped: false,
      scaleMode: "CROP",
      updatedAt: new Date()
    })
  );
});

test("bob cannot write to alice vcam config", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/configs/default`).set({
      userId: ALICE_UID,
      isMasterEnabled: true,
      updatedAt: new Date()
    })
  );
});
