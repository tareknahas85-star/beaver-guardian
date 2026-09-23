# Shared assets

Reusable source art. These masters live outside the git repo — the versions the
app actually ships are under `beaver-guardian/app/src/main/res/`.

| File | What it is |
|---|---|
| `tarek-signature.png` | Tarek's official e-signature. 675×351, RGBA, transparent background, ink `#1A202E`. **Use this file whenever something needs his handwritten signature.** |
| `beaver-guardian-icon.png` | App icon master. 526×526, RGBA, transparent squircle corners. Every mipmap density is derived from this. |

Regenerate app resources from these masters rather than re-cropping screenshots —
the backgrounds have already been removed cleanly.
