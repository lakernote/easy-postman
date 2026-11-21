# EasyPostman API data

This directory is an EasyPostman workspace; load it with "Open Folder" in EasyPostman. File format reference:
https://github.com/lakernote/easy-postman/blob/master/docs/WORKSPACE_FILES_zh.md

## Files
- collections.json: collections and folders (type=group) and requests (type=request, fields under data)
- environments.json: environments and their variables (variableList)
- functional_config.json: functional tests; rows[].requestItemId references a request id
- performance_config.json, mock_servers.json: prefer editing in the EasyPostman desktop app

## Editing rules
- Read existing nodes first and copy the field layout of similar nodes; do not invent fields, and keep existing fields when editing.
- Give every new node a new UUID id; change the id when duplicating a node.
- Use variables such as {{baseUrl}} and {{token}} for hosts and secrets; never write real secrets.
- Take paths, methods, parameters and bodies from this repository's source code.
- Keep the JSON valid with 4-space indentation; only change files in this directory.

## Verify
java -jar easy-postman.jar collection run <this-directory> -e "<environment>"
java -jar easy-postman.jar functional run <this-directory>
Exit code 0 means everything passed.
