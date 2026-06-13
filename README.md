# 雪线之上 · 本地媒体收藏夹

一个纯静态网页，用于解析并收藏公开分享内容的图片 / 视频 URL。

配色：

- 长空青：`#62909B`
- 琥珀：`#C78444`
- 普鲁士蓝：`#2C303B`
- 燕麦色：`#AB977E`

## 文件结构

```text
index.html
style.css
app.js
data/collection.json
README.md
```

## 功能

- 粘贴抖音、小红书或其他平台分享链接
- 调用 BugPk 免费 API 解析
- 支持直接粘贴图片 URL 收藏
- 收藏数据保存在浏览器 `localStorage`
- 支持导出 JSON
- 支持导入 JSON
- 图片直接预览
- 视频显示封面并可播放
- 支持搜索和类型筛选

## 接入的 API

- 抖音：`https://api.bugpk.com/api/douyin?url=`
- 小红书：`https://api.bugpk.com/api/xhsjx?url=`
- 聚合：`https://api.bugpk.com/api/short_videos?url=`

如果浏览器因为 CORS 无法直接请求接口，可以把链接发给雨檐，由雨檐代解析后再整理。

## 边界

仅用于收藏公开、本人拥有或已授权内容。不会绕过登录、付费、DRM 或私密权限。
