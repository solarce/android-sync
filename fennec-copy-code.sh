export LC_ALL=C # Ensure consistent sort order across platforms.
SORT_CMD="sort -f"

DIR=$(dirname "$0")
if [[ ! -d "$DIR" ]]; then DIR="$PWD"; fi
. "$DIR/fennec-paths.sh"
  
echo "Copying to $ANDROID ($SERVICES)..."

WARNING="These files are managed in the android-sync repo. Do not modify directly, or your changes will be lost."
echo "Creating README.txt."
echo $WARNING > $SERVICES/README.txt

echo "Copying background tests..."
BACKGROUND_TESTS_DIR=$ANDROID/tests/background/junit3
mkdir -p $BACKGROUND_TESTS_DIR

BACKGROUND_SRC_DIR="test/src/org/mozilla/gecko/background"
BACKGROUND_TESTHELPERS_SRC_DIR="src/main/java/org/mozilla/gecko/background/testhelpers"

BACKGROUND_TESTS_JAVA_FILES=$(find \
  $BACKGROUND_SRC_DIR/* \
  $BACKGROUND_TESTHELPERS_SRC_DIR/* \
  -name '*.java' \
  | sed "s,^$BACKGROUND_SRC_DIR,src," \
  | sed "s,^$BACKGROUND_TESTHELPERS_SRC_DIR,src/testhelpers," \
  | $SORT_CMD)

BACKGROUND_TESTS_RES_FILES=$(find \
  "test/res" \
  -type f \
  | sed "s,^test/res,res," \
  | $SORT_CMD)

mkdir -p $BACKGROUND_TESTS_DIR/src
rsync -C -a \
  $BACKGROUND_SRC_DIR/* \
  $BACKGROUND_TESTS_DIR/src

mkdir -p $BACKGROUND_TESTS_DIR/src/testhelpers
rsync -C -a \
  $BACKGROUND_TESTHELPERS_SRC_DIR/* \
  $BACKGROUND_TESTS_DIR/src/testhelpers

rsync -C -a \
  test/res \
  $BACKGROUND_TESTS_DIR

rsync -C -a \
  test/AndroidManifest.xml.in \
  $BACKGROUND_TESTS_DIR
echo "Copying background tests... done."

echo "Copying manifests..."
rsync -a manifests $SERVICES/

echo "Copying sources. All use of R must be compiled with Fennec."
SOURCEROOT="src/main/java/org/mozilla/gecko"
SYNCSOURCEDIR="$SOURCEROOT/sync"
BACKGROUNDSOURCEDIR="$SOURCEROOT/background"
SOURCEFILES=$(find "$BACKGROUNDSOURCEDIR" "$SYNCSOURCEDIR" \
  -name '*.java' \
  -and -not -name 'AnnouncementsConstants.java' \
  -and -not -name 'HealthReportConstants.java' \
  -and -not -name 'GlobalConstants.java' \
  -and -not -name 'BrowserContract.java' \
  -and -not -name 'AppConstants.java' \
  -and -not -name 'SysInfo.java' \
  -and -not -name 'SyncConstants.java' \
  -and -not -path '*testhelpers*' \
  | sed "s,$SOURCEROOT/,," | $SORT_CMD)

rsync -C \
  --exclude 'AppConstants.java' \
  --exclude 'SysInfo.java' \
  --exclude 'SyncConstants.java' \
  --exclude 'BrowserContract.java' \
  --exclude '*.in' \
  --exclude '*testhelper*' \
  -a $SYNCSOURCEDIR $ANDROID/base/

rsync -C \
  --exclude 'AppConstants.java' \
  --exclude 'SysInfo.java' \
  --exclude 'GlobalConstants.java' \
  --exclude 'AnnouncementsConstants.java' \
  --exclude 'HealthReportConstants.java' \
  --exclude '*.in' \
  --exclude '*testhelper*' \
  -a $BACKGROUNDSOURCEDIR $ANDROID/base/

echo "Copying preprocessed constants files."

# The grep line removes files in the root: those are provided by
# Fennec itself.
PREPROCESS_FILES=$(find \
  "$SOURCEROOT" \
  -name '*.java.in' \
  | grep "$SOURCEROOT/.*/" \
  | sed "s,.java.in,.java," \
  | sed "s,$SOURCEROOT/,," | $SORT_CMD)
for i in $PREPROCESS_FILES; do
# Just in case, delete the processed version.
  rm -f "$ANDROID/base/$i";
  cp "$SOURCEROOT/$i.in" "$ANDROID/base/$i.in";
done

echo "Copying internal dependency sources."
mkdir -p $ANDROID/thirdparty/ch/boye/httpclientandroidlib/
mkdir -p $ANDROID/thirdparty/org/json/simple/
mkdir -p $ANDROID/thirdparty/org/mozilla/apache/

APACHEDIR="src/main/java/org/mozilla/apache"
APACHEFILES=$(find "$APACHEDIR" -name '*.java' | sed "s,$APACHEDIR,org/mozilla/apache," | $SORT_CMD)
rsync -C -a "$APACHEDIR/" "$ANDROID/thirdparty/org/mozilla/apache"

echo "Copying external dependency sources."
JSONLIB=external/json-simple-1.1/src/org/json/simple
HTTPLIB=external/httpclientandroidlib/httpclientandroidlib/src/ch/boye/httpclientandroidlib
JSONLIBFILES=$(find "$JSONLIB" -name '*.java' | sed "s,$JSONLIB,org/json/simple," | $SORT_CMD)
HTTPLIBFILES=$(find "$HTTPLIB" -name '*.java' | sed "s,$HTTPLIB,ch/boye/httpclientandroidlib," | $SORT_CMD)
rsync -C -a "$HTTPLIB/" "$ANDROID/thirdparty/ch/boye/httpclientandroidlib/"
rsync -C -a "$JSONLIB/" "$ANDROID/thirdparty/org/json/simple/"
cp external/json-simple-1.1/LICENSE.txt $ANDROID/thirdparty/org/json/simple/

# Write a list of files to a Makefile variable.
# turn
# VAR:=1.java 2.java
# into
# VAR:=\
#   1.java \
#   2.java \
#   $(NULL)
function dump_mkfile_variable {
    output_file=$MKFILE
    variable_name=$1
    shift

    echo "$variable_name := \\" >> $output_file
    for var in "$@" ; do
        for f in $var ; do
            echo "  $f \\" >> $output_file
        done
    done
    echo "  \$(NULL)" >> $output_file
    echo "" >> $output_file
}

# Write a list of files to a mozbuild variable.
# turn
# VAR:=1.java 2.java
# into
# VAR += [\
#   '1.java',
#   '2.java',
# ]
function dump_mozbuild_variable {
    output_file=$1
    variable_name=$2
    shift
    shift

    echo "$variable_name [" >> $output_file
    for var in "$@" ; do
        for f in $var ; do
            echo "    '$f'," >> $output_file
        done
    done
    echo "]" >> $output_file
}

# Prefer PNGs in drawable-*: Android lint complains about PNG files in drawable.
SYNC_RES=$(find res -name 'sync*' \( -name '*.xml' -or -name '*.png' \) | sed 's,res/,resources/,' | $SORT_CMD)

# Creating moz.build file for Mozilla.
MOZBUILDFILE=$ANDROID/base/android-services.mozbuild
echo "Creating moz.build file for including in the Mozilla build system at $MOZBUILDFILE"
cat tools/mozbuild_mpl.txt > $MOZBUILDFILE

dump_mozbuild_variable $MOZBUILDFILE "sync_thirdparty_java_files =" "$HTTPLIBFILES" "$JSONLIBFILES" "$APACHEFILES"
echo >> $MOZBUILDFILE
dump_mozbuild_variable $MOZBUILDFILE "sync_java_files =" "$SOURCEFILES"
echo >> $MOZBUILDFILE
dump_mozbuild_variable $MOZBUILDFILE "sync_generated_java_files =" $(echo "$PREPROCESS_FILES" | sed "s,^,org/mozilla/gecko/,")

# Creating Makefile for Mozilla.
MKFILE=$ANDROID/tests/background/junit3/android-services-files.mk
echo "Creating background tests makefile for including in the Mozilla build system at $MKFILE"
cat tools/makefile_mpl.txt > $MKFILE
echo "# $WARNING" >> $MKFILE
dump_mkfile_variable "BACKGROUND_TESTS_JAVA_FILES" "$BACKGROUND_TESTS_JAVA_FILES"

# Creating moz.build for Mozilla.
MOZBUILDFILE=$ANDROID/tests/background/junit3/android-services.mozbuild
echo "Creating background tests moz.build file for including in the Mozilla build system at $MOZBUILDFILE"
cat tools/mozbuild_mpl.txt > $MOZBUILDFILE

# Finished creating Makefile for Mozilla.

echo "Writing README."
echo $WARNING > $ANDROID/base/sync/README.txt
echo $WARNING > $ANDROID/thirdparty/ch/boye/httpclientandroidlib/README.txt

echo "Copying resources..."
# I'm guessing these go here.
rsync -a --exclude 'icon.png' --exclude 'ic_status_logo.png' res/drawable $ANDROID/base/resources/
rsync -a --exclude 'icon.png' --exclude 'ic_status_logo.png' res/drawable-hdpi $ANDROID/base/resources/
rsync -a --exclude 'icon.png' --exclude 'ic_status_logo.png' res/drawable-mdpi $ANDROID/base/resources/
rsync -a --exclude 'icon.png' --exclude 'ic_status_logo.png' res/drawable-ldpi $ANDROID/base/resources/
rsync -a res/layout/*.xml $ANDROID/base/resources/layout/
rsync -a res/values/sync_styles.xml $ANDROID/base/resources/values/
rsync -a res/values-v11/sync_styles.xml $ANDROID/base/resources/values-v11/
rsync -a res/values-large-v11/sync_styles.xml $ANDROID/base/resources/values-large-v11/
rsync -a res/xml/*.xml $ANDROID/base/resources/xml/
rsync -a strings/strings.xml.in $SERVICES/
rsync -a strings/sync_strings.dtd.in $ANDROID/base/locales/en-US/sync_strings.dtd

echo "Done."
