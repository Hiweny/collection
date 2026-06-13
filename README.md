# 雪线之上 · Media Collection

一个用于收藏公开分享内容媒体直链的静态网页。

配色：

- 长空青：`#62909B`
- 琥珀：`#C78444`
- 普鲁士蓝：`#2C303B`
- 燕麦色：`#AB977E`

## 文件结构

```text
index.html
data/collection.json
README.md
```

## 数据说明

收藏数据位于：

```text
data/collection.json
```

每个收藏项支持：

- 图片预览：`coverUrl` 或 `mediaUrls[0]`
- 视频预览：`videoUrl` + `coverUrl`
- 平台筛选：`platform`
- 类型筛选：`type`，可为 `image` / `video` / `mixed`

以后把抖音、小红书、快手等公开分享链接发给雨檐，她会解析可公开访问的图片或视频直链，并追加到这个 JSON 文件。

## 边界

仅用于收藏公开、本人拥有或已授权内容。不会绕过登录、付费、DRM 或私密权限。
