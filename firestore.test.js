const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date();
}

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

test("Unauthenticated user cannot read or write profiles, contacts, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("messages").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("messages").doc("m1").set({
      userId: ALICE_UID,
      conversationId: "c1",
      senderId: ALICE_UID,
      senderName: "Alice",
      content: "Hello",
      messageType: "TEXT",
      readStatus: "SENT",
      sentAtMs: 1000,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});

test("Owner can create and read own profile and private PII doc; cross-user get/write fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = nowTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice",
      bio: "Hello",
      statusText: "En ligne",
      birthDay: 14,
      birthMonth: 7,
      birthdayPrivacy: "CONTACTS",
      timezoneId: "Europe/Paris",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").set({
      userId: ALICE_UID,
      countryCode: "+33",
      phoneMasked: "+33 •••• 78",
      birthYear: 1995,
      readReceiptsEnabled: true,
      fcmToken: "fcm_token_alice_v1",
      fcmTokenUpdatedAtMs: 1700000000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("account").update({
      fcmToken: "fcm_token_alice_v2",
      fcmTokenUpdatedAtMs: 1700005000,
      updatedAt: nowTs(),
    })
  );

  // Bob cannot read or overwrite Alice's profile or private PII / FCM token
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("account").update({
      fcmToken: "hijacked_token",
      updatedAt: nowTs(),
    })
  );
});

test("Shadow update with ghost field and identity spoofing are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowTs();

  // Ghost field injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      username: "alice",
      birthdayPrivacy: "CONTACTS",
      timezoneId: "Europe/Paris",
      isVerifiedAdmin: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Identity spoofing (userId mismatch)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_1").set({
      userId: BOB_UID,
      conversationId: "dm_bob",
      senderId: ALICE_UID,
      senderName: "Alice",
      content: "Spoof",
      messageType: "TEXT",
      readStatus: "SENT",
      sentAtMs: 1000,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Valid message create, edit, list, and invalid status rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowTs();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_valid_1").set({
      userId: ALICE_UID,
      conversationId: "dm_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      content: "Bonjour Prunelle",
      messageType: "TEXT",
      readStatus: "SENT",
      sentAtMs: 123456,
      isEdited: false,
      isForwarded: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_valid_1").update({
      content: "Bonjour Prunelle (modifié)",
      isEdited: true,
      updatedAt: nowTs(),
    })
  );

  // Invalid enum value
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("messages").doc("msg_valid_1").update({
      readStatus: "HACKED",
      updatedAt: nowTs(),
    })
  );
});

test("Resource poisoning and oversized ID are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = nowTs();
  const badId = "a".repeat(130);

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc(badId).set({
      userId: ALICE_UID,
      displayName: "Too Long Id",
      username: "toolong",
      isBlocked: false,
      isReported: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});
