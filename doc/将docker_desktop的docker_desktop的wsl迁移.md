你希望将本次Docker容器存储位置迁移到E盘的完整操作过程、遇到的问题及解决方案进行总结，以下是系统化、条理清晰的全流程复盘：

## 一、核心目标
将Docker（WSL 2后端）的容器、镜像等核心数据，从默认C盘迁移到E盘，释放C盘空间，且保证Docker后续正常运行。

## 二、完整操作流程（最终落地版）
全程需以**管理员身份运行PowerShell**，核心采用WSL「导出-注销-导入」三步法，具体步骤如下：
1.  **前期准备**
    - 关闭Docker Desktop（右键右下角图标选择「Quit Docker Desktop」）；
    - 执行 `wsl --shutdown` 彻底关闭所有WSL子系统，避免数据损坏；
    - 在E盘创建专属存储目录：`mkdir E:\DockerWSL`。
2.  **导出Docker相关WSL子系统（数据备份）**
    - 因查询仅存在 `docker-desktop` 子系统（无 `docker-desktop-data`），执行导出命令：`wsl --export docker-desktop E:\DockerWSL\docker-backup.tar`；
    - 等待备份完成（耗时取决于数据大小，不中断命令），生成备份文件 `docker-backup.tar`。
3.  **注销原有C盘WSL子系统**
    - 执行命令注销默认存储的 `docker-desktop`：`wsl --unregister docker-desktop`；
    - 可通过 `wsl -l -v` 验证，确认该子系统已从列表中消失。
4.  **导入子系统到E盘（完成迁移）**
    - 执行导入命令，指定WSL 2版本和E盘目标目录：`wsl --import docker-desktop E:\DockerWSL\data E:\DockerWSL\docker-backup.tar --version 2`；
    - 导入完成后，E:\DockerWSL\data 目录会生成WSL虚拟磁盘文件，存储所有Docker数据。
5.  **验证迁移结果**
    - 启动Docker Desktop，等待右下角图标变为绿色稳定状态；
    - 执行 `wsl -l -v` 验证，`docker-desktop` 状态变为 `Running`；
    - 执行测试命令 `docker run hello-world`，容器正常运行即表示迁移成功；
    - （可选）迁移稳定后，删除 `E:\DockerWSL\docker-backup.tar` 释放磁盘空间。

## 三、过程中遇到的2个核心问题及解决方案
### 问题1：执行导出命令时报错 `WSL_E_DISTRO_NOT_FOUND`（不存在对应分发）
1.  报错原因：手动手写的WSL子系统名称（初始以为是 `docker-desktop-data`）与系统实际存在的名称不匹配，且查询发现仅存在 `docker-desktop`（数据整合到该子系统，无独立数据子系统）。
2.  解决方案：
    - 先执行 `wsl -l -v` 查询当前所有已注册的WSL子系统，确认准确名称；
    - 将导出命令中的子系统名称替换为实际查询到的 `docker-desktop`，重新执行导出命令即可。

### 问题2：执行导入命令时报错 `ERROR_ALREADY_EXISTS`（已存在同名分发）
1.  报错原因：系统中残留了 `docker-desktop` 子系统的注册信息，或之前的注销操作未成功，无法重复导入同名子系统。
2.  解决方案：
    - 先执行 `wsl --shutdown` 关闭所有WSL子系统，避免子系统被占用；
    - 重新执行 `wsl --unregister docker-desktop` 彻底注销同名子系统；
    - 若仍报错，重启WSL核心服务：`net stop LxssManager` → `net start LxssManager`，再注销后导入；
    - 验证注销成功（`wsl -l -v` 无 `docker-desktop`），再执行导入命令即可。

## 四、关键补充说明
1.  本次迁移的核心是WSL子系统的虚拟磁盘，而非Docker文件共享目录，后者仅用于容器访问Windows本地目录，与容器核心存储无关；
2.  Docker Desktop启动时会自动唤醒 `docker-desktop` 子系统，无需手动启动子系统，日常使用直接打开Docker Desktop即可；
3.  迁移后所有新的Docker数据（新拉取镜像、新创建容器）都会自动存储在E盘，不再占用C盘空间，且原有数据完整保留。