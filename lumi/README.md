# edu-sharing lumi server

## Local

- Install dependencies: `npm install`
- Fetch H5P JS scripts: `/bin/bash/ {project_dir}/download-core.sh 1.26 1.25`. "1.26" and "1.25" are version numbers of the h5p-php-library and the h5p-editor-php-library respectively and can be replaced by other versions.
- Start MinIO and MongoDb: `docker compose up -d` 
- Start the server: `npm start`. It is configured to run on port 3000 locally