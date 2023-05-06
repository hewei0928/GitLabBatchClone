### 通过gitlab Api自动下载gitLab上的所有项目

全量拉取跨境项目。

### 步骤：

1.  进入[链接](http://my.office.hupun.com:5180/profile/personal_access_tokens) 生成个人private_toke, 并配置到application.yml 文件下的 `git.privateToken`。
2. 修改`git.projectDir` 为自己本地保存目录。
3. 运行`GitlabProjectsCloneApplication`即可。
4. 不想拉取的分组或项目可在`git.ignores`中添加。