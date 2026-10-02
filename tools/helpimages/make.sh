#!/bin/bash
# Regenerates the pictures for the in-app Help screen (apk/res/drawable-nodpi/help_*.png)
# from the real control deck. Needs Python 3 with Pillow and the DejaVu fonts.
set -euo pipefail
cd "$(dirname "$0")"
ROOT=$(cd ../.. && pwd); W=$(mktemp -d)
mkdir -p $W/src $W/cls $W/deck
cp GameViewStub.java.txt $W/src/GameView.java
javac -nowarn -d $W/cls $ROOT/src/android/modmarine/app/{Deck,PixelArt,Icons}.java $W/src/GameView.java HelpRender.java
(cd $W/deck && java -cp $W/cls modmarine.app.HelpRender)
DECKTEST=$W/deck python3 helpimgs.py $ROOT/apk/res/drawable-nodpi
rm -rf $W
