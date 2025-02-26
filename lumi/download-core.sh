# Call this script with the version of the H5P core as the first argument
# (= version tag of h5p-php-library) and the H5P editor as the second argument
# (= version tag of h5p-editor-php-library).
# Example: scripts/download-core.sh 1.24.0 1.24.1

# This script is based on the download-core.sh script provided by the lumi project.
# In order to facilitate easier H5P core updates it has been modified by metaVentis GmbH

core_version=$1
if [ ! -z "$2" ]
then
    editor_version=$2
else
    editor_version=$1
fi

if [ -z "$core_version" ]
then
    echo "You must add the H5P core version as the first argument to this script."
    exit
fi

echo "Downloading H5P core v$core_version..."
echo "Downloading H5P editor v$editor_version..."

h5p="$(dirname $0)/h5p"
mkdir -p "$h5p/tmp"
mkdir -p "$h5p/tmp/core"
mkdir -p "$h5p/tmp/editor"
mkdir -p "$h5p/core"
mkdir -p "$h5p/editor"
mkdir -p "$h5p/libraries"

rm -rf "$h5p/tmp"/*
rm -rf "$h5p/core"/*
rm -rf "$h5p/editor"/*

core=https://github.com/h5p/h5p-php-library/archive/$core_version.zip
editor=https://github.com/h5p/h5p-editor-php-library/archive/$editor_version.zip

curl -L $core -o"$h5p/tmp/core.zip"
unzip -a "$h5p/tmp/core.zip" -d"$h5p/tmp/core"
curl -L $editor -o"$h5p/tmp/editor.zip"
unzip -a "$h5p/tmp/editor.zip" -d"$h5p/tmp/editor"
mv "$h5p/tmp/core/h5p-php-library-$core_version"/* "$h5p/core/"
mv "$h5p/tmp/editor/h5p-editor-php-library-$editor_version"/* "$h5p/editor/"

rm -rf "$h5p/tmp"

IFS='.' read -r major minor patch <<< "$core_version"
src="$(dirname $0)/src"
sed -i -r "s|h5p_core_version_major=.*|h5p_core_version_major=${major}|g" "${src}/h5p.settings.ts"
sed -i -r "s|h5p_core_version_minor=.*|h5p_core_version_minor=${minor}|g" "${src}/h5p.settings.ts"
sed -i -r "s|h5p_core_version_patch=.*|h5p_core_version_patch=${patch}|g" "${src}/h5p.settings.ts"


