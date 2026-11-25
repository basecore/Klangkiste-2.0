# PWA → KlangKiste 2.0 data migration

The importer supports the PWA `FULL_BACKUP_V1` object and the older JSON array. It maps tag IDs, metadata, expected filenames, progress, completed/hidden flags, settings and statistics. PWA backup JSON excludes audio bytes, cover bytes, custom background bytes and (in the audited exporter) CUE chapter data.

After JSON import, use the SAF file picker to relink files by filename. **Reference** mode stores a persistable SAF URI; **managed copy** puts byte-for-byte media copies in app-private storage. Managed copies are removed on uninstall. Media is never stored as a Room BLOB.
