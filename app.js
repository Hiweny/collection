const KEY='snowline_media_collection_v2';
const C='https://api.cors.syrins.tech/?url=';
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
function fetchWithTimeout(url,ms){
  const ctrl=new AbortController();
  const timer=setTimeout(()=>ctrl.abort(),ms);
  return fetch(url,{signal:ctrl.signal}).finally(()=>clearTimeout(timer));
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

// ---- parseUrl helper (from randomness) ----
function parseUrl(url){
  if(/douyin|iesdouyin/.test(url)) return {platform:'douyin',type:'video',id:null};
  if(/xiaohongshu|xhslink/.test(url)) return {platform:'xhs',type:'image',id:null};
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

// ---- tryDouyinParse (from randomness) ----
async function tryDouyinParse(item,url){
  const apiUrl='https://api.bugpk.com/api/douyin?url='+encodeURIComponent(url);
  const sources=[
    {label:'直连',fetch:()=>fetchWithTimeout(apiUrl,15000)},
    {label:'代理',fetch:()=>fetchWithTimeout(C+apiUrl,10000)}
  ];
  for(const src of sources){
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
        if(item.videoUrl)item.type='video';
        else if(item.mediaUrls.length)item.type='image';
        item.platform='douyin';item.tags=['douyin'];
        return true;
      }
    }catch(e){continue}
  }
  showToast('抖音解析失败：请检查链接是否正确，或稍后重试');
  return false;
}

// ---- tryXhsParse (from randomness) ----
async function tryXhsParse(item,url){
  const apiUrl='https://api.bugpk.com/api/xhsjx?url='+encodeURIComponent(url);
  const sources=[
    {label:'直连',fetch:()=>fetchWithTimeout(apiUrl,10000)},
    {label:'代理',fetch:()=>fetchWithTimeout(C+apiUrl,10000)}
  ];
  for(const src of sources){
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
        if(item.videoUrl)item.type='video';
        else if(item.mediaUrls.length)item.type='image';
        item.platform='xhs';item.tags=['xhs'];
        return true;
      }
    }catch(e){continue}
  }
  showToast('小红书解析失败：请检查链接是否正确，或稍后重试');
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

  // 抖音 / 小红书 解析
  let parsed=parseUrl(src);
  if(mode==='auto'&&parsed&&(parsed.platform==='douyin'||parsed.platform==='xhs')){
    mode=parsed.platform;
  }

  if(mode==='douyin'){
    status.textContent='抖音解析中...';
    let item={id:'douyin_'+Date.now(),platform:'douyin',type:'video',title:'',author:'',sourceUrl:src,resolvedUrl:src,coverUrl:'',mediaUrls:[],videoUrl:'',tags:['douyin'],createdAt:new Date().toISOString(),note:''};
    let ok=await tryDouyinParse(item,src);
    if(ok){
      if(item.videoUrl&&(item.videoUrl.includes('/api/')||item.videoUrl.includes('.php'))){
        item._apiUrl=item.videoUrl;
        item.note='⚠️ 此视频来自随机 API，每次打开可能不同。点击下方「刷新」可重新加载。';
      }
      add(item);$('source').value='';status.textContent='';
      showToast('抖音解析成功 ✓');
    }
    return;
  }

  if(mode==='xhs'){
    status.textContent='小红书解析中...';
    let item={id:'xhs_'+Date.now(),platform:'xhs',type:'image',title:'',author:'',sourceUrl:src,resolvedUrl:src,coverUrl:'',mediaUrls:[],videoUrl:'',tags:['xhs'],createdAt:new Date().toISOString(),note:''};
    let ok=await tryXhsParse(item,src);
    if(ok){
      add(item);$('source').value='';status.textContent='';
      showToast('小红书解析成功 ✓');
    }
    return;
  }

  // fallback: 直接 API 调用
  let p=plat(src,mode);
  status.textContent='解析中...';
  try{
    let r=await fetchWithTimeout(api(p)+'?url='+encodeURIComponent(src),10000);
    let j=await r.json();
    if(j.code&&j.code!==200)throw new Error(j.msg||'解析失败');
    let it=norm(j,src,p);
    if(it.videoUrl&&(it.videoUrl.includes('/api/')||it.videoUrl.includes('.php'))){
      it._apiUrl=it.videoUrl;
      it.note='⚠️ 此视频来自随机 API，每次打开可能不同。点击下方「刷新」可重新加载。';
    }
    add(it);$('source').value='';status.textContent='';
    showToast('解析成功，已保存到本地 ✓');
  }catch(e){showToast('解析失败：'+e.message+'。可能是接口跨域或接口临时不可用。')}
}

function plat(u,m){if(m==='douyin'||/douyin|iesdouyin/.test(u))return'douyin';if(m==='xhs'||/xiaohongshu|xhslink/.test(u))return'xhs';return'short'}
function api(p){return{douyin:'https://api.bugpk.com/api/douyin',xhs:'https://api.bugpk.com/api/xhsjx',short:'https://api.bugpk.com/api/short_videos'}[p]}

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
    return '<div class="gallery"><div class="stack s2"></div><div class="stack s1"></div><img class="page" src="'+esc(u)+'" referrerpolicy="no-referrer" onerror="favImgFallback(this)" data-fallback="'+esc(u)+'"><button class="flip prev" onclick="flip(\''+it.id+'\',-1)">‹</button><button class="flip next" onclick="flip(\''+it.id+'\',1)">›</button><span class="count">'+(idx+1)+'/'+imgs.length+'</span></div>';
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

// ---- flip ----
function flip(id,step){
  let it=items.find(x=>x.id===id);
  if(!it)return;
  let n=(it.mediaUrls||[]).length;
  if(n<2)return;
  it._idx=((it._idx||0)+step+n)%n;
  render();
}

// ---- card ----
function card(it){
  let media=mediaBlock(it);
  let links=(it.mediaUrls||[]).slice(0,5).map((u,i)=>'<a class="small" target="_blank" href="'+esc(u)+'">图'+(i+1)+'</a>').join('')+(it.videoUrl?'<a class="small" target="_blank" href="'+esc(it.videoUrl)+'">视频</a>':'');
  return '<article class="item"><div class="media">'+media+'</div><div class="body"><div class="meta"><span class="tag">'+esc(it.platform)+'</span><span class="type">'+esc(it.type)+'</span></div><p class="title">'+esc(it.title)+'</p><a class="url" target="_blank" href="'+esc(it.sourceUrl)+'">'+esc(it.sourceUrl)+'</a><div class="links">'+links+'<button class="small" onclick="del(\''+it.id+'\')">删除</button></div></div></article>';
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

// ---- Back to top: show only when scrolling UP ----
(function(){
  let lastScrollY=window.scrollY;
  const backtop=document.getElementById('backtop');
  if(!backtop)return;
  window.addEventListener('scroll',()=>{
    const y=window.scrollY;
    if(y<200){backtop.classList.remove('show');}
    else if(y<lastScrollY){backtop.classList.add('show');}
    else{backtop.classList.remove('show');}
    lastScrollY=y;
  },{passive:true});
})();

load();