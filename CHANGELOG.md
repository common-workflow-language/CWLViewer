
# Changelog 

## v1.3.1 [unreleased]
WIP

## v1.3.0 [6-07-2018]
### Added
- [Permalinks](https://github.com/common-workflow-language/cwlviewer/pull/175) with content negotiation
- Show workflow [license](https://github.com/common-workflow-language/cwlviewer/pull/198)
- Added [Privacy Policy](https://github.com/common-workflow-language/cwlviewer/pull/194)
### Fixed
- Support packed workflows in [permalinks](https://github.com/common-workflow-language/cwlviewer/pull/177)
- Use [Python 3 and nodejs](https://github.com/common-workflow-language/cwlviewer/pull/197)
- Support [git tags](https://github.com/common-workflow-language/cwlviewer/pull/192) 
- UI: [Shrink description text](https://github.com/common-workflow-language/cwlviewer/pull/178)

## v1.2.2 [24-08-2017]
### Fixed
- Fix for invalid branch names being accepted in some circumstances
- Fix regression in supporting slashes in branch names
- [References](https://doi.org/10.5281/zenodo.848163)

## v1.2.1 [22-08-2017]
### Added
- Adds directory listing functionality
### Fixed
- Better support for packed workflows
    *  Use %26 (#) in the URL to view individual workflows as per cwltool
    * Links to subworkflows within a packed file now function correctly
- Improved error reporting
- Fix for array and optional types not being parsed correctly
- Fix for twitter meta tags not containing the correct image URL
- Fix for a concurrency issue
- Brings API documentation up to date with the current state of the JSON API

## v1.2.0 [22-08-2017]
- This release contains no changes - please use [v1.2.1](https://github.com/common-workflow-language/cwlviewer/releases/tag/v1.2.1) instead
- [References](https://doi.org/10.5281/zenodo.846747)

## v1.1.1 [11-08-2017]
### Fixed
- This patch release of CWL Viewer fixes documentation and a potential git checkout issue.
- [References](https://doi.org/10.5281/zenodo.841782)

## v.1.1.0 [11-08-2017]
- Support for any workflow stored in a Git repository
   * Specific additional features eg linking directly to files for Github, Gitlab, Bitbucket
- Adds cwltool use for parsing - uses RDF representation with triple store
- Intermediate loading screen
- Research Object enhancements
   * Add visualisation images as bundled aggregates
   * RDF representation as annotation
   * Packed version of workflow files as annotation
   * Git2prov link for the repository as history
   * Schema.org support for author attribution in CWL descriptions
- API and documentation backing current features
- Visualisation improvements
   * Intermediate value names
- Ontology information linked and name given for format fields on inputs and outputs
- "About" page with best practices for writing CWL for parsing by the viewer
- Label and description searching on the explore page
- Various bug fixes
- [References](https://doi.org/10.7490/f1000research.1114375.1)

## v1.0.1 [11-08-2017]
### Fixed
- Bug fixes and attribution.
- [References](https://doi.org/10.5281/zenodo.841680)

## v1.0 [06-07-2017]
- [References](https://doi.org/10.5281/zenodo.823295)

