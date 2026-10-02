// Plain-node test of the notification rule. Run: node test/notificationRule.test.js
// No emulator or credentials needed — the rule is a pure function.

const assert = require("assert");

// Duplicated here so the test does not initialise the Admin SDK.
function notificationBodyFor(message) {
  if (!message || message.type !== "TEXT") return "Notification";
  const text = message.text;
  if (typeof text !== "string") return "Notification";
  return text === "Imp" || text === "imp" ? "Alter" : "Notification";
}

const cases = [
  ["Imp", "Alter"],
  ["imp", "Alter"],
  ["Hello", "Notification"],
  ["Important", "Notification"],
  ["Imp please call", "Notification"],
  ["IMP", "Notification"],
  ["imp!", "Notification"],
  [" imp", "Notification"],
  ["imp ", "Notification"],
  ["Imp\n", "Notification"],
  ["", "Notification"],
  ["iMp", "Notification"],
  ["impp", "Notification"]
];

let failures = 0;
for (const [text, expected] of cases) {
  const actual = notificationBodyFor({ type: "TEXT", text });
  const ok = actual === expected;
  if (!ok) failures++;
  console.log(`${ok ? "pass" : "FAIL"}  ${JSON.stringify(text)} -> ${actual} (expected ${expected})`);
}

// Media messages are never "Alter".
assert.strictEqual(notificationBodyFor({ type: "IMAGE" }), "Notification");
assert.strictEqual(notificationBodyFor({ type: "AUDIO" }), "Notification");
assert.strictEqual(notificationBodyFor(null), "Notification");

console.log(failures === 0 ? "\nAll notification rule cases pass." : `\n${failures} case(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
