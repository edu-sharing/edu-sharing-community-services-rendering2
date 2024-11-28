# edu-sharing lumi server

## Run locally

- Make sure that MongoDB and MinIO connection details are set correctly in .env
- Make sure that the buckets specified in .env are present in MinIO

- Install dependencies: `npm install`
- Fetch H5P Core and Editor: `npm run setup`
- Build: `npm run build`
- Start: `npm run start`

## H5P Editor and Core updates

In order to **update H5P-Editor** to another version change the respective version in the "setup" script in package.json.

```
"setup": "rm -rf ./h5p && ./download-core.sh 1.27.0 1.25"
```

The editor version is the second argument (1.25 in the example above). Don't forget to run the script and rebuild the application:

- Fetch H5P: `npm run setup`
- Build: `npm run build`

The same process applies to **H5P-Core updates**. The version is provided in the first param (1.27.0 in the example). Please note that in contrast to the H5P-Editor a patch version **MUST** be provided.


