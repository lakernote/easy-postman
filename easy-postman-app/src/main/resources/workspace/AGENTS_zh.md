# EasyPostman 接口数据

本目录是 EasyPostman 工作区，用 EasyPostman「打开文件夹」即可加载。文件格式说明：
https://github.com/lakernote/easy-postman/blob/master/docs/WORKSPACE_FILES_zh.md

## 文件
- collections.json：集合、文件夹（type=group）和请求（type=request，内容在 data 中）
- environments.json：环境和变量（variableList）
- functional_config.json：功能测试，rows[].requestItemId 引用请求 id
- performance_config.json、mock_servers.json：建议在 EasyPostman 桌面端编辑

## 修改规则
- 先阅读已有节点，新增内容照搬同类节点的字段写法，不要自创字段；修改时保留已有字段。
- 新节点使用新的 UUID 作为 id；复制节点时必须更换 id。
- 地址、Token、密码使用变量（如 {{baseUrl}}、{{token}}），不要写入真实密钥。
- 接口的路径、方法、参数和请求体以本仓库源码为准。
- 保持 JSON 合法，4 空格缩进；只修改本目录中的文件。

## 验证
java -jar easy-postman.jar collection run <本目录> -e "<环境名>"
java -jar easy-postman.jar functional run <本目录>
退出码为 0 表示通过。
