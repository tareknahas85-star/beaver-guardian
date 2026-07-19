# 🔐 Privacy & Responsible Use

## The idea
Beaver Guardian is **transparent parental supervision** for a minor. It's built to be known to the kid, not hidden:
- The kid's device shows a **permanent notification**: "This device is supervised by a parent".
- The child setup screen tells them their device is being managed.

## What gets collected
- App usage times (minutes per day, per app).
- Call **info**: number, direction, time, duration. **No** audio recording (not technically possible anyway).
- Last device location.
- No message content, no passwords, no call audio. None of that is touched.

## Where it lives
- In **your own Firebase project** only. Nothing passes through anyone else's servers.
- You're in full control: you can wipe the data any time from the Firebase Console.

## Security tips
1. **Tighten your Firebase rules**: enable Authentication and restrict `.read`/`.write` to your account.
2. Don't share your real `google-services.json` publicly (the one in the repo is a placeholder).
3. Check the data now and then and delete old stuff.

## Legal limits
- OK: a parent supervising their own **minor** kid (under 18) with the kid's knowledge.
- Not OK and illegal: installing it on an **adult's** device without their knowledge and consent.
- As the kid gets older, talking beats silent monitoring.

> By using this project you take responsibility for following your country's laws.
