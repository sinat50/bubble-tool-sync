# Bubble Tool Sync

<img src="docs/icon.png" alt="Bubble Tool icon" width="96">

A RuneLite plugin for the **OSRS Bubble Tool** Android app. It shows your quests, skill levels and achievement
diary tasks as a QR code, and the app scans it with your phone's camera. Your progress goes straight from your
screen to your phone, with nothing sent over the internet.

## What it does

- While you're logged in, it reads your own account's quests, quest points, skill levels, account type and
  achievement diary tasks. It reads them again a few seconds after they change, and whenever you open the panel.
- It shows them in a side panel as a QR code, with your account name, quest points and the time they were read.
  After you log out, the panel keeps showing your last reading.
- It also saves the same text to a file on your PC, one file per account (`account-<account name>.txt` in
  RuneLite's `.runelite/plugin-data/bubble-tool-sync` folder).

It only reads your own account. It doesn't send anything anywhere, and it doesn't click or type anything for you.

## How to use it

1. Install **Bubble Tool Sync** from the Plugin Hub in RuneLite.
2. Log in to the game.
3. Open the Bubble Tool Sync panel from RuneLite's side bar.
4. In the OSRS Bubble Tool app on your phone, choose **Scan from RuneLite** and point the camera at the QR code.

If the code won't scan, turn up your screen's brightness, or move the phone a little closer or further away.

Other ways to move it:
- **Export file** saves the same text that's in the QR code to a file, in a folder you choose (for example one
  that syncs to your phone). Then import that file in the app.
- **Copy text** copies the same text to the clipboard.

## Credits

- QR codes are made with Project Nayuki's
  [QR Code generator library](https://www.nayuki.io/page/qr-code-generator-library), used under the MIT License:

  > Copyright (c) Project Nayuki. (MIT License)
  >
  > Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
  > documentation files (the "Software"), to deal in the Software without restriction, including without
  > limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
  > Software, and to permit persons to whom the Software is furnished to do so, subject to the following
  > conditions:
  > - The above copyright notice and this permission notice shall be included in all copies or substantial
  >   portions of the Software.
  > - The Software is provided "as is", without warranty of any kind, express or implied, including but not
  >   limited to the warranties of merchantability, fitness for a particular purpose and noninfringement. In no
  >   event shall the authors or copyright holders be liable for any claim, damages or other liability, whether
  >   in an action of contract, tort or otherwise, arising from, out of or in connection with the Software or the
  >   use or other dealings in the Software.

- Which game values hold which achievement diary tasks comes from
  [Quest Helper](https://github.com/Zoinkwiz/quest-helper) by Zoinkwiz and its contributors (BSD 2-Clause License).

- The icon is the OSRS Bubble Tool app's own icon, by sinat50.

## Licence

BSD 2-Clause. See [LICENSE](LICENSE).
