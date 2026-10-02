# App name and icon

## Name

`DSI`, set in `strings.xml`. This is what appears under the icon on the home screen.

## Icon: read this before shipping

The specification asks for the official DSATM logo. The repository ships a **neutral
placeholder** instead — a plain monogram on white at
`app/src/main/res/drawable/ic_launcher_foreground.xml`.

The reason: the DSATM mark belongs to Dayananda Sagar Academy of Technology and Management.
Putting an institution's mark on an unaffiliated private app misrepresents the app as
connected to that institution, which is a trademark issue regardless of who is using the app
or how privately. Downloading the logo from a website is not the same as being permitted to
use it, and the specification itself notes that the search is a requirement rather than
permission to use an unverified asset.

There is also a practical point. A recognisable college logo on the home screen is a label
that invites a question. A neutral icon does not. For an app whose whole design is about not
drawing attention, the placeholder serves the purpose better than the logo would.

### If you have permission

If the institution supplied the asset or gave written permission:

1. Put the source file (SVG or a 1024×1024 PNG) at `app/src/main/res/drawable/`.
2. In Android Studio: right-click `res` → New → Image Asset → Launcher Icons (Adaptive and
   Legacy). Keep the foreground inside the central 66% safe zone — adaptive icons are masked
   to a circle, squircle or rounded square depending on the launcher, and a logo drawn to the
   edges gets cropped.
3. Replace `ic_launcher_foreground.xml` with the generated asset. The adaptive icon XML and
   the white background colour are already wired up.
4. Confirm the final icon with the project owner before release, as the spec requires.

### If you do not

Keep the placeholder, or commission something original. Either is defensible. Using the mark
without permission is not.
