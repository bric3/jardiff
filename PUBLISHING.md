# Publishing Guide
       
Simple note on how to publish jardiff to Maven Central.

## Version Numbering

This project follows [Semantic Versioning](https://semver.org/):

## Release Process

Make the release on [GitHub releases](https://github.com/bric3/jardiff/releases), publish it.

### 1. Prepare the release

```bash
# Get latest tag
git pull
# Ensure clean working directory
git status

# Ensure build passes
./gradlew clean build
```
                   
Eventually inspect the deployment:

```bash
./gradlew nmcpZipAggregation
# Inspect the zip in build/nmcp/zip/aggregation.zip
```

Also, each published modules have their expanded form in `build/nmcp/m2/io/github/bric3/jardiff/`. 


```bash
./gradlew nmcpPublishAggregationToMavenLocal
# Inspect the publication in ~/.m2/repository/io/github/bric3/jardiff/
```

### 2. Publish to Maven Central

This project uses [nmcp](https://gradleup.com/nmcp/) plugin.
Following example 
* uses 1Password to get secrets (ids are fake), but other tool may apply
* uses file based signing

```bash
./gradlew publishAggregationToCentralPortal \
  -PmavenCentralUsername=$(op item get "central" --fields publication.username) \
  -PmavenCentralPassword=$(op item get "central" --fields publication.password --reveal) \
  -Psigning.secretKeyRingFile=$HOME/.gnupg/secring.gpg \
  -Psigning.password="$(op item get "gpg" --field passphrase --reveal)" \
  -Psigning.keyId=gpg-short-key-id
```

### 5. Verify Publication

Check Maven Central: https://search.maven.org/artifact/io.github.bric3.jardiff/jardiff

### GPG Signing Setup

If you need to set up GPG:

```bash
# List keys and note the key ID
gpg --list-secret-keys --keyid-format=short

# Export secret keyring
gpg --export-secret-keys > ~/.gnupg/secring.gpg
```

## Troubleshooting

- **1Password authentication expired**: Run `op signin` again
- **Invalid credentials**: Verify 1Password item names and field names
- **Signing failed**: Verify GPG key ID and passphrase are correct
- **Sync delay**: Maven Central sync can take 15-30 minutes after publication

