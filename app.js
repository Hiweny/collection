const KEY='snowline_media_collection_v2';
const C='https://api.cors.syrins.tech/?url=';
const IMGBED='https://api.yujn.cn/api/360_img.php';
// 360图床域名特征（ps.ssl.qhmsg.com 等），用于识别已转存的图片，避免重复转存
const IMGBED_DOMAINS=['ps.ssl.qhmsg.com','qhmsg.com','qhimg.com','360tpcdn.com'];
let items=[],filter='all';
const $=id=>document.getElementById(id),grid=$('grid'),empty=$('empty'),q=$('q'),status=$('status');

// ---- Toast ----
function showToast(msg){
  let t=document.getElementById('toast');
  if(!t){
    t=document.createElement('div');t.id='toast';
    t.style.cssText='position:fixed;top:20px;left:50%;transform:translateX(-50%);z-index:9999;background:rgba(44,48,59,.92);color:#F6F1E8;padding:10px 24px;border-radius:20px;font-size:14px;pointer-events:none;transition:opacity .3s;opacity:0;border:1px solid rgba(246,241,232,.18);backdrop-filter:blur(12px)';
    document.body.appendChild(t);
  }
  t.textContent=msg;t.style.opacity='1';
  clearTimeout(t._tid);t._tid=setTimeout(()=>{t.style.opacity='0'},2500);
}

// ---- fetchWithTimeout (AbortController) ----
function fetchWithTimeout(url,ms,opts){
  const ctrl=new AbortController();
  const timer=setTimeout(()=>ctrl.abort(),ms);
  const fetchOpts=opts?Object.assign({},opts,{signal:ctrl.signal}):{signal:ctrl.signal};
  return fetch(url,fetchOpts).finally(()=>clearTimeout(timer));
}

// ---- Image Bed (360图床) Conversion ----
// 将图片链接转存到360图床，获取永久链接
// 策略：方式1/2 是服务端抓取（不受防盗链影响，最可靠）
//       方式3/4 是浏览器端下载后base64上传（仅当图片允许跨域时有效）
async function imgbedOne(url,timeout){
  timeout=timeout||15000;
  const apiUrl=IMGBED+'?url='+encodeURIComponent(url);
  // 方式1：直连图床API（服务端抓取图片，不受防盗链/CORS影响）
  try{
    const r=await fetchWithTimeout(apiUrl,timeout);
    const j=await r.json();
    if(j.code===200&&j.url)return j.url;
  }catch(e){}
  // 方式2：CORS代理转发图床API（防止图床API本身被墙）
  try{
    const r=await fetchWithTimeout(C+apiUrl,timeout);
    const j=await r.json();
    if(j.code===200&&j.url)return j.url;
  }catch(e){}
  // 方式3：浏览器直接下载图片→base64→POST上传（适用于无防盗链的图片）
  try{
    const imgR=await fetchWithTimeout(url,30000);
    if(!imgR.ok)throw new Error('status '+imgR.status);
    const blob=await imgR.blob();
    const base64=await new Promise((resolve,reject)=>{
      const reader=new FileReader();
      reader.onload=()=>resolve(reader.result.split(',')[1]);
      reader.onerror=reject;
      reader.readAsDataURL(blob);
    });
    const formBody='base64='+encodeURIComponent(base64);
    const postR=await fetchWithTimeout(IMGBED,20000,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:formBody});
    const j=await postR.json();
    if(j.code===200&&j.url)return j.url;
  }catch(e){}
  // 方式4：CORS代理下载图片→base64→POST上传（兜底，突破防盗链）
  try{
    const imgR=await fetchWithTimeout(C+url,30000);
    if(!imgR.ok)throw new Error('status '+imgR.status);
    const blob=await imgR.blob();
    const base64=await new Promise((resolve,reject)=>{
      const reader=new FileReader();
      reader.onload=()=>resolve(reader.result.split(',')[1]);
      reader.onerror=reject;
      reader.readAsDataURL(blob);
    });
    const formBody='base64='+encodeURIComponent(base64);
    const postR=await fetchWithTimeout(IMGBED,20000,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:formBody});
    const j=await postR.json();
    if(j.code===200&&j.url)return j.url;
  }catch(e){}
  return null;
}

async function imgbedBatch(urls){
  if(!urls||!urls.length)return urls;
  const out=[];
  let skipped=0;
  for(let i=0;i<urls.length;i++){
    if(isImgbedUrl(urls[i])){
      out.push(urls[i]);skipped++;
    }else{
      status.textContent='图床转存中… '+(i+1)+'/'+urls.length;
      const converted=await imgbedOne(urls[i]);
      out.push(converted||urls[i]);
    }
  }
  if(skipped>0)console.log('已跳过 '+skipped+' 张已在图床的图片');
  return out;
}

// ---- save / load ----
function save(){
  const record={};
  items.forEach(it=>{if((it.mediaUrls||[]).length>1){record[it.id]=it._idx||0}});
  localStorage.setItem(KEY,JSON.stringify({updatedAt:new Date().toISOString(),items,_albumIdx:record}));
}
function load(){
  // 尝试新 key，再尝试旧 key（迁移）
  let raw={};
  let stored=localStorage.getItem(KEY);
  if(!stored){ stored=localStorage.getItem('snowline_media_collection_v1'); }
  try{raw=JSON.parse(stored||'{}')}catch(e){}
  items=raw.items||[];
  const record=raw._albumIdx||{};
  items.forEach(it=>{
    const n=(it.mediaUrls||[]).length;
    if(n>1){it._idx=((record[it.id]||0)+1)%n;record[it.id]=it._idx}
  });
  save();render();
}

// ---- helpers ----
function firstUrl(t){let m=String(t).match(/https?:\/\/[^\s，。]+/i);return m?m[0]:''}
function esc(s){return String(s||'').replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]))}
function isImageUrl(s){return /^https?:\/\/.+\.(png|jpe?g|webp|gif|avif)(\?.*)?$/i.test(s.trim())}
function isImgbedUrl(s){return IMGBED_DOMAINS.some(d=>s.includes(d))}
function allImgbed(imgs){return imgs&&imgs.length>0&&imgs.every(isImgbedUrl)}

// ---- parseUrl helper ----
function parseUrl(url){
  if(/douyin|iesdouyin/.test(url)) return {platform:'douyin',type:'video',id:null};
  if(/xiaohongshu|xhslink/.test(url)) return {platform:'xhs',type:'image',id:null};
  if(/bilibili|b23\.tv/.test(url)) return {platform:'bilibili',type:'video',id:null};
  if(/kuaishou|v\.kuaishou/.test(url)) return {platform:'kuaishou',type:'video',id:null};
  if(/toutiao|ixigua/.test(url)) return {platform:'toutiao',type:'video',id:null};
  if(/\/api\/short_videos/.test(url)) return {platform:'short',type:'video',id:null};
  if(/\/api\/random/.test(url)) return {platform:'direct',type:'image',id:null};
  if(/\/api\/dj/.test(url)) return {platform:'direct',type:'audio',id:null};
  if(/\.(png|jpe?g|webp|gif|avif)(\?|$)/i.test(url)) return {platform:'direct',type:'image',id:null};
  if(/\.(mp4|webm|mov)(\?|$)/i.test(url)) return {platform:'direct',type:'video',id:null};
  return null;
}

// ---- norm (from randomness) ----
function norm(d,src,p){
  let x=d.data||d,imgs=x.images||x.imgurl||x.pics||[],video=x.url||x.video||x.video_url||'';
  if(x.live_photo&&x.live_photo.length){imgs=x.live_photo.map(i=>i.image).filter(Boolean);if(!video)video=x.live_photo[0].video||''}
  let type=imgs.length?'image':(video?'video':'media');
  return{id:p+'_'+Date.now(),platform:p,type,title:x.title||x.desc||'Untitled',author:(x.author&&x.author.name)||x.author||'',sourceUrl:src,resolvedUrl:src,coverUrl:x.cover||x.coverUrl||imgs[0]||'',mediaUrls:imgs,videoUrl:video,tags:[p],createdAt:new Date().toISOString(),note:''};
}

function add(it){
  items.unshift(it);save();render();
}

// ---- tryMultiImage (existing) ----
function tryMultiImage(raw){
  const lines=raw.split(/\n+/).map(s=>s.trim()).filter(Boolean);
  if(lines.length<2)return null;
  const urls=lines.filter(isImageUrl);
  if(urls.length<2)return null;
  const titleLine=lines[0];
  const title=(titleLine&&!isImageUrl(titleLine))?titleLine:('手动图集 '+new Date().toLocaleDateString('zh-CN'));
  return{id:'album_'+Date.now(),platform:'manual-album',type:'image',title:title,sourceUrl:urls[0],resolvedUrl:urls[0],coverUrl:urls[0],mediaUrls:urls,videoUrl:'',tags:['album'],createdAt:new Date().toISOString(),note:''};
}

function addFromMulti(raw){
  const it=tryMultiImage(raw);
  if(!it)return false;
  add(it);$('source').value='';
  showToast('已添加图集 · '+it.mediaUrls.length+' 张 ✓');
  return true;
}

// ---- tryDouyinParse (yujn.cn primary → bugpk.com fallback) ----
async function tryDouyinParse(item,url){
  // 主接口：yujn.cn 抖音解析（返回格式：{msg,name,title,video,play_video,cover,images,type}）
  const yujnSources=[
    {label:'yujn直连',fetch:()=>fetchWithTimeout('https://api.yujn.cn/api/dy_jx.php?msg='+encodeURIComponent(url),15000)},
    {label:'yujn代理',fetch:()=>fetchWithTimeout(C+'https://api.yujn.cn/api/dy_jx.php?msg='+encodeURIComponent(url),12000)}
  ];
  for(const src of yujnSources){
    try{
      const r=await src.fetch();
      if(!r)continue;
      const j=await r.json();
      if(j.video||(j.images&&j.images.length)){
        item.title=j.title||item.title||'';
        item.author=j.name||'';
        item.coverUrl=j.cover||'';
        item.mediaUrls=j.images||[];
        item.videoUrl=j.video||j.play_video||'';
        if(item.videoUrl)item.type='video';
        else if(item.mediaUrls.length)item.type='image';
        item.platform='douyin';item.tags=['douyin'];
        return true;
      }
    }catch(e){}
  }
  // 备用接口：bugpk.com 抖音解析
  const bugpkUrl='https://api.bugpk.com/api/douyin?url='+encodeURIComponent(url);
  const bugpkSources=[
    {label:'bugpk直连',fetch:()=>fetchWithTimeout(bugpkUrl,15000)},
    {label:'bugpk代理',fetch:()=>fetchWithTimeout(C+bugpkUrl,10000)}
  ];
  for(const src of bugpkSources){
    try{
      let r=await src.fetch();
      if(!r)continue;
      let j=await r.json();
      if(j.code===200&&j.data){
        let d=j.data;
        item.title=d.title||d.desc||item.title||'';
        item.author=d.author?.name||d.author||'';
        item.coverUrl=d.cover||d.coverUrl||'';
        let imgs=d.images||d.imgurl||d.pics||[];
        let vid=d.url||d.video||d.video_url||d.videoUrl||'';
        if(d.live_photo&&d.live_photo.length){imgs=d.live_photo.map(i=>i.image).filter(Boolean);if(!vid)vid=d.live_photo[0].video||''}
        item.mediaUrls=imgs;
        item.videoUrl=vid;
        if(d.video_backup&&Array.isArray(d.video_backup)&&d.video_backup.length){
          item._videoBackups=d.video_backup.map(b=>b.url||b).filter(Boolean);
        }
        if(d.music&&d.music.url){
          item._musicUrl=d.music.url;
          item._musicName=d.music.name||d.music.title||'';
        }
        if(item.videoUrl)item.type='video';
        else if(item.mediaUrls.length)item.type='image';
        item.platform='douyin';item.tags=['douyin'];
        return true;
      }
    }catch(e){continue}
  }
  console.log('所有抖音专用接口均失败，将回退到聚合解析');
  return false;
}

// ---- tryAggregateParse (yujn.cn dspjx.php 优先 → bugpk.com short_videos 备用, 覆盖20+平台) ----
async function tryAggregateParse(item,url){
  // 主接口：yujn.cn 聚合解析 dspjx.php（支持抖音/快手/B站/小红书/微博/皮皮虾等20+平台）
  const yujnUrl='https://api.yujn.cn/api/dspjx.php?type=json&url='+encodeURIComponent(url);
  const yujnSources=[
    {label:'yujn直连',fetch:()=>fetchWithTimeout(yujnUrl,20000)},
    {label:'yujn代理',fetch:()=>fetchWithTimeout(C+yujnUrl,15000)}
  ];
  for(const src of yujnSources){
    try{
      let r=await src.fetch();
      if(!r)continue;
      let j=await r.json();
      if(j.code===200&&j.data){
        let d=j.data;
        item.title=d.title||item.title||'';
        item.coverUrl=d.img&&d.img.length?d.img[0]:'';
        item.mediaUrls=d.img||[];
        item.videoUrl=d.video||'';
        if(item.videoUrl)item.type='video';
        else if(item.mediaUrls.length)item.type='image';
        item.platform='aggregate';item.tags=['aggregate'];
        return true;
      }
    }catch(e){continue}
  }
  // 备用：bugpk.com 短视频聚合解析（已不稳定，仅作兜底）
  const bugpkUrl='https://api.bugpk.com/api/short_videos?url='+encodeURIComponent(url);
  const bugpkSources=[
    {label:'bugpk直连',fetch:()=>fetchWithTimeout(bugpkUrl,20000)},
    {label:'bugpk代理',fetch:()=>fetchWithTimeout(C+bugpkUrl,15000)}
  ];
  for(const src of bugpkSources){
    try{
      let r=await src.fetch();
      if(!r)continue;
      let j=await r.json();
      if(j.code===200&&j.data){
        let d=j.data;
        item.title=d.title||d.desc||'';
        item.author=(d.author&&d.author.name)||d.author||'';
        item.coverUrl=d.cover||'';
        let imgs=d.images||d.imgurl||d.pics||[];
        let vid=d.url||d.video||d.video_url||'';
        if(d.live_photo&&d.live_photo.length){
          imgs=d.live_photo.map(i=>i.image).filter(Boolean);
          if(!vid)vid=d.live_photo[0].video||'';
        }
        item.mediaUrls=imgs;
        item.videoUrl=vid;
        if(d.video_backup&&Array.isArray(d.video_backup)&&d.video_backup.length){
          item._videoBackups=d.video_backup.map(b=>b.url||b).filter(Boolean);
        }
        if(d.music&&d.music.url){
          item._musicUrl=d.music.url;
          item._musicName=d.music.name||d.music.title||'';
        }
        if(item.videoUrl)item.type='video';
        else if(item.mediaUrls.length)item.type='image';
        else item.type='media';
        if(d.type==='douyin'||/douyin/.test(url))item.platform='douyin';
        else if(d.type==='xhs'||/xiaohongshu|xhslink/.test(url))item.platform='xhs';
        else item.platform='aggregate';
        item.tags=[item.platform];
        return true;
      }
    }catch(e){continue}
  }
  return false;
}

// ---- 提取小红书 xsec_token ----
function extractXsecToken(url){
  let m=url.match(/[?&]xsec_token=([^&]+)/);
  return m?m[1]:null;
}

// ---- 通用短链接解析 (douyin/kuaishou/bilibili/xiaohongshu/toutiao) ----
// 短链接需要在浏览器端解析成完整 URL，服务端 API 可能无法解析短链接
async function resolveShortLink(url){
  const isShort=/v\.douyin\.com|b23\.tv|v\.kuaishou\.com|xhslink\.com|t\.toutiao\.com/.test(url);
  if(!isShort)return null;
  const sources=[
    {label:'直连',fetch:()=>fetchWithTimeout(url,10000,{redirect:'follow'})},
    {label:'代理',fetch:()=>fetchWithTimeout(C+url,10000,{redirect:'follow'})}
  ];
  for(const src of sources){
    try{
      const r=await src.fetch();
      const finalUrl=r.url;
      if(finalUrl&&finalUrl!==url&&!finalUrl.includes('bugpk.com')){
        console.log('短链接解析成功:',finalUrl);
        return finalUrl;
      }
    }catch(e){}
  }
  return null;
}

// ---- tryXhsParse (yujn.cn primary → bugpk.com xhsjx → 聚合兜底) ----
// 官方文档推荐的 bugpk.com 小红书接口是 /api/xhsjx（综合解析，支持视频+图集+实况照片）
// /api/xhs 和 /api/xhsimg 已废弃，统一使用 /api/xhsjx
async function tryXhsParse(item,url){
  let xsec=extractXsecToken(url);
  // 主接口：yujn.cn 小红书解析（不需要 xsec_token，返回格式：{code,msg,name,title,desc,images}）
  const yujnSources=[
    {label:'yujn直连',fetch:()=>fetchWithTimeout('https://api.yujn.cn/api/xhs.php?url='+encodeURIComponent(url),15000)},
    {label:'yujn代理',fetch:()=>fetchWithTimeout(C+'https://api.yujn.cn/api/xhs.php?url='+encodeURIComponent(url),15000)}
  ];
  for(const src of yujnSources){
    try{
      const r=await src.fetch();
      if(!r)continue;
      const j=await r.json();
      if(j.code===200&&j.images&&j.images.length){
        item.title=j.title||j.desc||'';
        item.author=j.name||'';
        item.coverUrl=j.images[0]||'';
        item.mediaUrls=j.images;
        item.type='image';
        item.platform='xhs';item.tags=['xhs'];
        return true;
      }
      if(j.code===201&&j.msg){console.log('yujn.cn 小红书:',j.msg);}
    }catch(e){}
  }
  // 备用接口：bugpk.com 小红书综合解析 /api/xhsjx（官方推荐，需要 xsec_token）
  if(xsec){
    let xhsjxUrl='https://api.bugpk.com/api/xhsjx?url='+encodeURIComponent(url)+'&xsec_token='+encodeURIComponent(xsec);
    const jxSources=[
      {label:'xhsjx直连',fetch:()=>fetchWithTimeout(xhsjxUrl,12000)},
      {label:'xhsjx代理',fetch:()=>fetchWithTimeout(C+xhsjxUrl,12000)}
    ];
    for(const src of jxSources){
      try{
        let r=await src.fetch();
        if(!r)continue;
        let j=await r.json();
        if(j.code===200&&j.data){
          let d=j.data;
          item.title=d.title||d.desc||'';
          item.author=(d.author&&d.author.name)||d.author||'';
          item.coverUrl=d.cover||'';
          let imgs=d.images||d.imgurl||d.pics||[];
          let vid=d.url||d.video||d.video_url||'';
          if(d.live_photo&&d.live_photo.length){
            imgs=d.live_photo.map(i=>i.image).filter(Boolean);
            if(!vid)vid=d.live_photo[0].video||'';
          }
          item.mediaUrls=imgs;
          item.videoUrl=vid;
          if(item.videoUrl)item.type='video';
          else if(item.mediaUrls.length)item.type='image';
          item.platform='xhs';item.tags=['xhs'];
          return true;
        }
      }catch(e){continue}
    }
  } else {
    console.log('小红书链接缺少 xsec_token，跳过 bugpk.com 专用接口，将回退到聚合解析');
  }
  return false;
}

// ---- tryBilibiliParse (yujn.cn blbl.php primary → bugpk.com fallback) ----
async function tryBilibiliParse(item,url){
  // 主接口：yujn.cn B站解析（返回格式：{code,msg,title,imgurl,desc,data:[{video_url,...}],user:{name,user_img}}）
  const yujnSources=[
    {label:'yujn直连',fetch:()=>fetchWithTimeout('https://api.yujn.cn/api/blbl.php?url='+encodeURIComponent(url),20000)},
    {label:'yujn代理',fetch:()=>fetchWithTimeout(C+'https://api.yujn.cn/api/blbl.php?url='+encodeURIComponent(url),15000)}
  ];
  for(const src of yujnSources){
    try{
      let r=await src.fetch();
      if(!r)continue;
      let j=await r.json();
      if((j.code===1||j.code===200)&&j.data&&j.data.length){
        let d=j.data[0];
        item.title=j.title||d.title||'';
        item.author=(j.user&&j.user.name)||'';
        item.coverUrl=j.imgurl||'';
        item.mediaUrls=j.imgurl?[j.imgurl]:[];
        item.videoUrl=d.video_url||d.url||'';
        if(item.videoUrl)item.type='video';
        else if(item.mediaUrls.length)item.type='image';
        item.platform='bilibili';item.tags=['bilibili'];
        return true;
      }
    }catch(e){}
  }
  // 备用：bugpk.com（已不稳定，仅作兜底）
  const bugpkUrl='https://api.bugpk.com/api/bilibili?url='+encodeURIComponent(url);
  try{
    let r=await fetchWithTimeout(bugpkUrl,15000);
    if(!r)throw new Error('no response');
    let j=await r.json();
    if(j.code===200&&j.data){
      let d=j.data;
      item.title=d.title||d.desc||'';
      item.author=(d.author&&d.author.name)||d.author||'';
      item.coverUrl=d.cover||'';
      item.mediaUrls=d.images||d.imgurl||d.pics||[];
      item.videoUrl=d.url||d.video||d.video_url||'';
      if(item.videoUrl)item.type='video';
      else if(item.mediaUrls.length)item.type='image';
      item.platform='bilibili';item.tags=['bilibili'];
      return true;
    }
  }catch(e){}
  return false;
}

// ---- tryKuaishouParse (yujn.cn kuaishou.php primary → bugpk.com fallback) ----
async function tryKuaishouParse(item,url){
  // 主接口：yujn.cn 快手解析（返回格式：{code:200,msg,data:{title,cover,url,type}}）
  const yujnSources=[
    {label:'yujn直连',fetch:()=>fetchWithTimeout('https://api.yujn.cn/api/kuaishou.php?url='+encodeURIComponent(url),20000)},
    {label:'yujn代理',fetch:()=>fetchWithTimeout(C+'https://api.yujn.cn/api/kuaishou.php?url='+encodeURIComponent(url),15000)}
  ];
  for(const src of yujnSources){
    try{
      let r=await src.fetch();
      if(!r)continue;
      let j=await r.json();
      if(j.code===200&&j.data){
        let d=j.data;
        item.title=d.title||'';
        item.coverUrl=d.cover||'';
        item.mediaUrls=[];
        item.videoUrl=d.url||'';
        if(item.videoUrl)item.type='video';
        item.platform='kuaishou';item.tags=['kuaishou'];
        return true;
      }
    }catch(e){}
  }
  // 备用：bugpk.com（已不稳定，仅作兜底）
  const bugpkUrl='https://api.bugpk.com/api/ksjx?url='+encodeURIComponent(url);
  try{
    let r=await fetchWithTimeout(bugpkUrl,15000);
    if(!r)throw new Error('no response');
    let j=await r.json();
    if(j.code===200&&j.data){
      let d=j.data;
      item.title=d.title||d.desc||'';
      item.author=(d.author&&d.author.name)||d.author||'';
      item.coverUrl=d.cover||'';
      item.mediaUrls=d.images||d.imgurl||d.pics||[];
      item.videoUrl=d.url||d.video||d.video_url||'';
      if(item.videoUrl)item.type='video';
      else if(item.mediaUrls.length)item.type='image';
      item.platform='kuaishou';item.tags=['kuaishou'];
      return true;
    }
  }catch(e){}
  return false;
}

// ---- tryToutiaoParse (yujn.cn dspjx.php 聚合 → bugpk.com fallback) ----
// 头条/西瓜视频没有专用API，使用聚合解析
async function tryToutiaoParse(item,url){
  // 主接口：yujn.cn 聚合解析（支持头条/西瓜）
  const yujnUrl='https://api.yujn.cn/api/dspjx.php?type=json&url='+encodeURIComponent(url);
  try{
    let r=await fetchWithTimeout(yujnUrl,20000);
    let j=await r.json();
    if(j.code===200&&j.data){
      let d=j.data;
      item.title=d.title||item.title||'';
      item.coverUrl=d.img&&d.img.length?d.img[0]:'';
      item.mediaUrls=d.img||[];
      item.videoUrl=d.video||'';
      if(item.videoUrl)item.type='video';
      else if(item.mediaUrls.length)item.type='image';
      item.platform='toutiao';item.tags=['toutiao'];
      return true;
    }
  }catch(e){}
  // 通过代理重试
  try{
    let r=await fetchWithTimeout(C+yujnUrl,15000);
    let j=await r.json();
    if(j.code===200&&j.data){
      let d=j.data;
      item.title=d.title||item.title||'';
      item.coverUrl=d.img&&d.img.length?d.img[0]:'';
      item.mediaUrls=d.img||[];
      item.videoUrl=d.video||'';
      if(item.videoUrl)item.type='video';
      else if(item.mediaUrls.length)item.type='image';
      item.platform='toutiao';item.tags=['toutiao'];
      return true;
    }
  }catch(e){}
  // 备用：bugpk.com（已不稳定，仅作兜底）
  try{
    let r=await fetchWithTimeout('https://api.bugpk.com/api/toutiao?url='+encodeURIComponent(url),15000);
    if(!r)throw new Error('no response');
    let j=await r.json();
    if(j.code===200&&j.data){
      let d=j.data;
      item.title=d.title||d.desc||'';
      item.author=(d.author&&d.author.name)||d.author||'';
      item.coverUrl=d.cover||'';
      item.mediaUrls=d.images||d.imgurl||d.pics||[];
      item.videoUrl=d.url||d.video||d.video_url||'';
      if(item.videoUrl)item.type='video';
      else if(item.mediaUrls.length)item.type='image';
      item.platform='toutiao';item.tags=['toutiao'];
      return true;
    }
  }catch(e){}
  return false;
}

// ---- parseAdd (improved) ----
async function parseAdd(){
  const raw=$('source').value.trim();
  if(!raw){showToast('没有内容');return;}

  // 先检测多行直链图集
  if(addFromMulti(raw))return;

  let src=firstUrl(raw);
  let mode=$('api').value;
  if(!src){showToast('没有找到链接');return;}

  // 如果是短链接，先解析成完整 URL（提高 API 解析成功率）
  // 短链接: v.douyin.com, b23.tv, v.kuaishou.com, xhslink.com, t.toutiao.com
  if(/v\.douyin\.com|b23\.tv|v\.kuaishou\.com|xhslink\.com|t\.toutiao\.com/.test(src)){
    status.textContent='解析短链接中…';
    const resolved=await resolveShortLink(src);
    if(resolved){src=resolved;console.log('短链接已解析为:',src);}
    else{status.textContent='短链接解析失败，使用原始链接尝试…';}
  }

  // 直接图片 URL
  if(mode==='direct-image'||/\.(png|jpe?g|webp|gif|avif)(\?|$)/i.test(src)){
    add({id:'img_'+Date.now(),platform:'direct',type:'image',title:'图片直链',sourceUrl:src,resolvedUrl:src,coverUrl:src,mediaUrls:[src],videoUrl:'',tags:['direct'],createdAt:new Date().toISOString(),note:''});
    $('source').value='';
    showToast('已添加图片直链 ✓');
    return;
  }

  // 短视频随机 API
  if(mode==='short'||/\/api\/short_videos/.test(src)){
    status.textContent='解析中...';
    try{
      let r=await fetchWithTimeout('https://api.bugpk.com/api/short_videos?url='+encodeURIComponent(src),10000);
      let j=await r.json();
      if(j.code&&j.code!==200)throw new Error(j.msg||'解析失败');
      let it=norm(j,src,'short');
      if(it.videoUrl&&(it.videoUrl.includes('/api/')||it.videoUrl.includes('.php'))){
        it._apiUrl=it.videoUrl;
        it.note='⚠️ 此视频来自随机 API，每次打开可能不同。点击下方「刷新」可重新加载。';
      }
      add(it);$('source').value='';status.textContent='';
      showToast('解析成功，已保存到本地 ✓');
    }catch(e){showToast('解析失败：'+e.message)}
    return;
  }

  // 抖音 / 小红书 / B站 / 快手 / 头条 解析
  let parsed=parseUrl(src);
  if(mode==='auto'&&parsed&&['douyin','xhs','bilibili','kuaishou','toutiao'].includes(parsed.platform)){
    mode=parsed.platform;
  }

  if(mode==='douyin'){
    status.textContent='抖音解析中...';
    let item={id:'douyin_'+Date.now(),platform:'douyin',type:'video',title:'',author:'',sourceUrl:src,resolvedUrl:src,coverUrl:'',mediaUrls:[],videoUrl:'',tags:['douyin'],createdAt:new Date().toISOString(),note:''};
    let ok=await tryDouyinParse(item,src);
    // 抖音解析失败 → 自动回退到聚合解析
    if(!ok){
      status.textContent='抖音解析失败，尝试聚合解析...';
      ok=await tryAggregateParse(item,src);
    }
    if(ok){
      // 图片转存到图床（防止防盗链失效）
      if(item.mediaUrls&&item.mediaUrls.length){
        status.textContent='图片转存中…';
        item.mediaUrls=await imgbedBatch(item.mediaUrls);
        if(item.coverUrl){
          const cv=await imgbedOne(item.coverUrl);
          if(cv)item.coverUrl=cv;
        }
        item._imgbedConverted=true;
      }
      if(item.videoUrl&&(item.videoUrl.includes('/api/')||item.videoUrl.includes('.php'))){
        item._apiUrl=item.videoUrl;
        item.note='⚠️ 此视频来自随机 API，每次打开可能不同。点击下方「刷新」可重新加载。';
      }
      add(item);$('source').value='';status.textContent='';
      showToast('解析成功 ✓（'+item.platform+'）');
    }
    return;
  }

  if(mode==='xhs'){
    status.textContent='小红书解析中...';
    let item={id:'xhs_'+Date.now(),platform:'xhs',type:'image',title:'',author:'',sourceUrl:src,resolvedUrl:src,coverUrl:'',mediaUrls:[],videoUrl:'',tags:['xhs'],createdAt:new Date().toISOString(),note:''};
    let ok=await tryXhsParse(item,src);
    // 小红书解析失败 → 自动回退到聚合解析
    if(!ok){
      status.textContent='小红书解析失败，尝试聚合解析...';
      ok=await tryAggregateParse(item,src);
    }
    if(ok){
      // 图片转存到图床（防止防盗链失效）
      if(item.mediaUrls&&item.mediaUrls.length){
        status.textContent='图片转存中…';
        item.mediaUrls=await imgbedBatch(item.mediaUrls);
        if(item.coverUrl){
          const cv=await imgbedOne(item.coverUrl);
          if(cv)item.coverUrl=cv;
        }
        item._imgbedConverted=true;
      }
      add(item);$('source').value='';status.textContent='';
      showToast('解析成功 ✓（'+item.platform+'）');
    }
    return;
  }

  // ---- B站 / 快手 / 头条 解析 ----
  if(mode==='bilibili'||mode==='kuaishou'||mode==='toutiao'){
    status.textContent=(mode==='bilibili'?'B站':mode==='kuaishou'?'快手':'头条')+'解析中...';
    let item={id:mode+'_'+Date.now(),platform:mode,type:'video',title:'',author:'',sourceUrl:src,resolvedUrl:src,coverUrl:'',mediaUrls:[],videoUrl:'',tags:[mode],createdAt:new Date().toISOString(),note:''};
    let ok;
    if(mode==='bilibili')ok=await tryBilibiliParse(item,src);
    else if(mode==='kuaishou')ok=await tryKuaishouParse(item,src);
    else ok=await tryToutiaoParse(item,src);
    // 平台专用解析失败 → 自动回退到聚合解析
    if(!ok){
      status.textContent='专用解析失败，尝试聚合解析...';
      ok=await tryAggregateParse(item,src);
    }
    if(ok){
      if(item.mediaUrls&&item.mediaUrls.length){
        status.textContent='图片转存中…';
        item.mediaUrls=await imgbedBatch(item.mediaUrls);
        if(item.coverUrl){
          const cv=await imgbedOne(item.coverUrl);
          if(cv)item.coverUrl=cv;
        }
        item._imgbedConverted=true;
      }
      add(item);$('source').value='';status.textContent='';
      showToast('解析成功 ✓（'+item.platform+'）');
    }
    return;
  }

  // ---- 聚合解析兜底（支持20+平台自动识别：B站/快手/微博/Ins/YouTube等） ----
  if(mode==='auto'||mode==='general'){
    status.textContent='聚合解析中...';
    let item={id:'aggr_'+Date.now(),platform:'aggregate',type:'media',title:'',author:'',sourceUrl:src,resolvedUrl:src,coverUrl:'',mediaUrls:[],videoUrl:'',tags:['aggregate'],createdAt:new Date().toISOString(),note:''};
    let ok=await tryAggregateParse(item,src);
    if(ok){
      // 图片转存到图床（防止防盗链失效）
      if(item.mediaUrls&&item.mediaUrls.length){
        status.textContent='图片转存中…';
        item.mediaUrls=await imgbedBatch(item.mediaUrls);
        if(item.coverUrl){
          const cv=await imgbedOne(item.coverUrl);
          if(cv)item.coverUrl=cv;
        }
        item._imgbedConverted=true;
      }
      if(item.videoUrl&&(item.videoUrl.includes('/api/')||item.videoUrl.includes('.php'))){
        item._apiUrl=item.videoUrl;
        item.note='⚠️ 此视频来自随机 API，每次打开可能不同。点击下方「刷新」可重新加载。';
      }
      add(item);$('source').value='';status.textContent='';
      showToast('聚合解析成功 ✓（'+item.platform+'）');
    }
    return;
  }
}

// ---- reconvertItemImages (将已有收藏的图片重新转存到图床) ----
async function reconvertItemImages(id){
  let it=items.find(x=>x.id===id);
  if(!it)return;
  let urls=it.mediaUrls||[];
  if(!urls.length){showToast('该项目没有图片需要转存');return;}
  // 检查是否全部已在图床
  if(allImgbed(urls)&&isImgbedUrl(it.coverUrl||'')){
    showToast('图片已全部在图床，无需转存 ✓');
    return;
  }
  status.textContent='图床转存中…';
  let converted=await imgbedBatch(urls);
  let changed=0;
  for(let i=0;i<urls.length;i++){
    if(converted[i]&&converted[i]!==urls[i]){it.mediaUrls[i]=converted[i];changed++}
  }
  if(it.coverUrl&&!isImgbedUrl(it.coverUrl)){
    const cv=await imgbedOne(it.coverUrl);
    if(cv&&cv!==it.coverUrl){it.coverUrl=cv;changed++}
  }
  it._imgbedConverted=true;
  save();render();status.textContent='';
  if(changed>0)showToast('已转存 '+changed+' 张图片到图床 ✓');
  else showToast('图片已全部在图床，无需转存');
}

// ---- batchReconvertAll (批量转存全部图片) ----
async function batchReconvertAll(){
  let allImgItems=items.filter(i=>(i.mediaUrls||[]).length>0);
  if(!allImgItems.length){showToast('没有需要转存的图片');return;}
  // 过滤掉已全部转存的项目
  let needConvert=allImgItems.filter(i=>!allImgbed(i.mediaUrls||[])||!isImgbedUrl(i.coverUrl||''));
  if(!needConvert.length){showToast('所有图片已在图床，无需转存 ✓');return;}
  if(!confirm('将转存 '+needConvert.length+' 个项目的图片到图床（共 '+allImgItems.length+' 个项目，其中 '+(allImgItems.length-needConvert.length)+' 个已转存）。可能需要较长时间。继续？'))return;
  let total=0,changed=0;
  for(let it of needConvert){
    status.textContent='批量转存中… '+(++total)+'/'+needConvert.length;
    let urls=it.mediaUrls||[];
    let converted=await imgbedBatch(urls);
    for(let i=0;i<urls.length;i++){
      if(converted[i]&&converted[i]!==urls[i]){it.mediaUrls[i]=converted[i];changed++}
    }
    if(it.coverUrl&&!isImgbedUrl(it.coverUrl)){
      const cv=await imgbedOne(it.coverUrl);
      if(cv&&cv!==it.coverUrl){it.coverUrl=cv;changed++}
    }
    it._imgbedConverted=true;
  }
  save();render();status.textContent='';
  showToast('批量转存完成！共转存 '+changed+' 张图片 ✓');
}

// ---- refreshFavVideo (from randomness) ----
async function refreshFavVideo(btn,id){
  let it=items.find(x=>x.id===id);
  if(!it||!it._apiUrl)return;
  btn.textContent='刷新中...';btn.disabled=true;
  try{
    let r=await fetchWithTimeout(it._apiUrl,15000);
    let j=await r.json();
    let d=j.data||j;
    let newUrl=d.url||d.video||d.video_url||d.videoUrl||it.videoUrl;
    if(newUrl){it.videoUrl=newUrl;save();render();showToast('已刷新视频 ✓');}
    else showToast('刷新失败：未获取到视频');
  }catch(e){showToast('刷新失败：'+e.message)}
}

// ---- favImgFallback (from randomness) ----
function favImgFallback(img){
  if(!img.dataset.fallback||img.dataset.tried)return;
  img.dataset.tried='1';
  img.src=C+img.dataset.fallback;
  img.onerror=function(){img.style.display='none';img.nextElementSibling?.style&&(img.nextElementSibling.style.display='flex')};
}

// ---- mediaBlock (improved) ----
function mediaBlock(it){
  let imgs=it.mediaUrls||[],idx=it._idx||0,cover=it.coverUrl||imgs[0]||'';
  if(imgs.length>1){
    let u=imgs[idx%imgs.length];
    return '<div class="gallery" id="gallery-'+it.id+'"><div class="stack s2"></div><div class="stack s1"></div><img class="page" src="'+esc(u)+'" referrerpolicy="no-referrer" onerror="favImgFallback(this)" data-fallback="'+esc(u)+'"><button class="flip prev" onclick="flip(\''+it.id+'\',-1)">‹</button><button class="flip next" onclick="flip(\''+it.id+'\',1)">›</button><span class="count">'+(idx+1)+'/'+imgs.length+'</span></div>';
  }
  if(it.videoUrl){
    let html='<video src="'+esc(it.videoUrl)+'" poster="'+esc(cover)+'" controls playsinline></video>';
    if(it._apiUrl){
      html+='<div class="refresh-bar"><button class="small refresh-btn" onclick="refreshFavVideo(this,\''+it.id+'\')">🔄 刷新视频</button></div>';
    }
    if(it.note){html+='<div class="note-bar">'+esc(it.note)+'</div>';}
    return html;
  }
  return cover?'<img src="'+esc(cover)+'" referrerpolicy="no-referrer" onerror="favImgFallback(this)" data-fallback="'+esc(cover)+'">':'<span>NO PREVIEW</span>';
}

// ---- flip (局部更新，不重建整个 grid) ----
function flip(id,step){
  let it=items.find(x=>x.id===id);
  if(!it)return;
  let n=(it.mediaUrls||[]).length;
  if(n<2)return;
  it._idx=((it._idx||0)+step+n)%n;
  save();
  // 只更新当前图集的图片和计数器，不触发全量 render
  let gallery=document.getElementById('gallery-'+id);
  if(!gallery)return;
  let img=gallery.querySelector('.page');
  let count=gallery.querySelector('.count');
  if(img){img.src=it.mediaUrls[it._idx]}
  if(count){count.textContent=(it._idx+1)+'/'+n}
}

// ---- card ----
function card(it){
  let media=mediaBlock(it);
  let hasImgs=(it.mediaUrls||[]).length>0;
  let allOnImgbed=hasImgs&&allImgbed(it.mediaUrls||[]);
  let links=(it.mediaUrls||[]).slice(0,5).map((u,i)=>'<a class="small" target="_blank" href="'+esc(u)+'">图'+(i+1)+'</a>').join('')+(it.videoUrl?'<a class="small" target="_blank" href="'+esc(it.videoUrl)+'">视频</a>':'');
  let reconvertBtn=hasImgs?'<button class="small reconvert" onclick="reconvertItemImages(\''+it.id+'\')" title="转存图片到图床防止防盗链失效">'+(allOnImgbed?'✅ 已转存':'🖼️ 转存图床')+'</button>':'';
  return '<article class="item"><div class="media">'+media+'</div><div class="body"><div class="meta"><span class="tag">'+esc(it.platform)+'</span><span class="type">'+esc(it.type)+'</span>'+(allOnImgbed?'<span class="tag" style="background:rgba(199,132,68,.2);border-color:rgba(199,132,68,.4)">🔒 图床</span>':'')+'</div><p class="title">'+esc(it.title)+'</p><a class="url" target="_blank" href="'+esc(it.sourceUrl)+'">'+esc(it.sourceUrl)+'</a><div class="links">'+links+reconvertBtn+'<button class="small" onclick="del(\''+it.id+'\')">删除</button></div></div></article>';
}

// ---- render ----
function render(){
  let key=(q.value||'').toLowerCase();
  let list=items.filter(i=>(filter==='all'||i.type===filter)&&JSON.stringify(i).toLowerCase().includes(key));
  grid.innerHTML=list.map(card).join('');
  empty.classList.toggle('hidden',list.length>0);
  $('total').textContent=items.length;$('imgs').textContent=items.filter(i=>i.type==='image').length;$('vids').textContent=items.filter(i=>i.type==='video').length;
}

// ---- del ----
function del(id){items=items.filter(i=>i.id!==id);save();render();}

// ---- events ----
document.addEventListener('click',e=>{
  let b=e.target.closest('[data-filter]');
  if(b){filter=b.dataset.filter;document.querySelectorAll('[data-filter]').forEach(x=>x.classList.toggle('active',x.dataset.filter===filter));render()}
});
q.oninput=render;
$('parseBtn').onclick=parseAdd;
$('manualBtn').onclick=()=>{
  const raw=$('source').value.trim();
  if(addFromMulti(raw))return;
  let u=firstUrl(raw);
  if(u){
    add({id:'manual_'+Date.now(),platform:'manual',type:'image',title:'手动收藏',sourceUrl:u,resolvedUrl:u,coverUrl:u,mediaUrls:[u],videoUrl:'',tags:['manual'],createdAt:new Date().toISOString(),note:''});
    $('source').value='';
    showToast('已添加 ✓');
  }
};
$('exportBtn').onclick=()=>{
  // 导出格式与 randomness 兼容：纯数组
  let a=document.createElement('a');
  a.href=URL.createObjectURL(new Blob([JSON.stringify(items,null,2)],{type:'application/json'}));
  a.download='snowline-collection.json';
  a.click();
};
$('importFile').onchange=e=>{
  let f=e.target.files[0];
  if(!f)return;
  f.text().then(t=>{
    let d=JSON.parse(t);
    // 兼容两种格式：纯数组 [...] 和 {items:[...]}
    let incoming=Array.isArray(d)?d:(d.items||[]);
    if(!incoming.length){showToast('没有可导入的数据');return;}
    items=[...incoming,...items];
    save();render();
    showToast('已导入 '+incoming.length+' 条收藏 ✓');
  }).catch(()=>showToast('导入失败：JSON 格式错误'));
};
$('clearBtn').onclick=()=>{
  if(confirm('清空本地收藏？')){
    items=[];save();render();
    showToast('已清空');
  }
};
$('reconvertAllBtn').onclick=batchReconvertAll;

// ---- Liquid Glass Mouse Tracking ----
(function(){
  function updateMouse(e){
    document.querySelectorAll('.card:hover, .item:hover').forEach(el => {
      const rect = el.getBoundingClientRect();
      const x = ((e.clientX - rect.left) / rect.width) * 100;
      const y = ((e.clientY - rect.top) / rect.height) * 100;
      el.style.setProperty('--mx', x + '%');
      el.style.setProperty('--my', y + '%');
    });
  }
  document.addEventListener('mousemove', updateMouse, {passive: true});
})();

load();