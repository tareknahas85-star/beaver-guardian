# Privacy and responsible use

## The main idea

Beaver Guardian is **open supervision** of a child under 18. It is built to be
known, not hidden:

- The child's phone shows a notification all the time: this phone is supervised.
- The child setup screen says clearly that this phone is the managed one.

If you want an app the child cannot see, this is the wrong app.

---

## What is collected

- How many minutes each app was used, per day.
- Call **details**: the number, in or out, the time, how long it lasted.
  **No sound is recorded** — Android does not allow it.
- The last known location of the phone.
- When call filtering is on: numbers that were blocked or that were unknown.
- Safe zone events: entering or leaving a place you defined.

## What is never collected

- The content of messages, in any app.
- Passwords.
- The sound of calls.
- Photos, files, or the camera.

---

## Where the data goes

Into **your own Firebase project only**. No other company's server is involved.
You own it and you can delete all of it at any time from the Firebase console.

---

## Security you must set up yourself

1. **Set the database rules.** Copy [`database.rules.json`](../database.rules.json)
   into the Realtime Database rules page. If you leave the rules open, anyone who
   finds your database address can read your child's location. This is the single
   most important step.
2. **Turn on Anonymous sign-in** in Firebase Authentication. The rules need it.
3. **Delete old data now and then.** Location history from six months ago serves
   no purpose.
4. `google-services.json` is not a secret — it ships inside every Android APK, so
   anyone can read it out of the app. Your protection is the database rules, not
   hiding that file.

---

## The law

- **Allowed:** a parent supervising their own child under 18, with the child
  knowing about it.
- **Not allowed, and a crime in most countries:** installing this on an adult's
  phone without their knowledge and agreement. That is stalkerware.

Laws differ by country. Using this project is your responsibility.

---

## A thought beyond the law

As a child grows, hidden monitoring costs more trust than it buys safety. Talking
usually works better than watching. Tools like this one are most useful for a
young child with a first phone, and least useful for a teenager.
