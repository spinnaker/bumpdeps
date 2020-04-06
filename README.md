# bumpdeps

Bumpdeps is a github action to automatically modify the version dependencies in a Spinnaker repo's
`gradle.properties` files. It requires the following inputs:

* **ref**: The git ref that triggered this workflow run. This should almost always be `${{ github.ref }}`
* **key**: The key in the `gradle.properties` file that will be modified (e.g. `korkVersion`)
* **repositories**: The list of repositories to modify (i.e. the ones that depend on this repository)

An example workflow looks something like this:

```yaml
name: Release

on:
  push:
    tags:
    - "v.+"

jobs:
  bump-dependencies:
    runs-on: ubuntu-latest
    steps:
    - uses: spinnaker/bumpdeps@master
      with:
        ref: ${{ github.ref }}
        key: korkVersion
        repositories: fiat,echo
      env:
        GITHUB_OAUTH: ${{ secrets.REPO_OAUTH_TOKEN }}
```
