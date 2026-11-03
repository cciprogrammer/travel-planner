/**
 * 腾讯地图 GL JS 加载与地理编码工具
 *
 * 合规说明：使用腾讯地图（合规地图服务商）。
 * - 默认场景：不携带 key，通过 _TMapSecurityConfig 走本地代理（WorkBuddy 运行时自动替换占位符）
 * - 自备 Key 场景：在 frontend/.env 中配置 VITE_TMAP_KEY，SDK 将携带该 key 请求
 * 无论哪种方式，代码中都不内置任何可用 Key。
 */

// WorkBuddy 运行时会自动替换这两个占位符，请勿手动修改
const TMAP_SERVICE_HOST =
  'http://127.0.0.1:__WB_HTTP_PORT__/_TMapService/_wbt/__WB_TMAP_SECRET__'

/** 用户自备的腾讯位置服务 Key（可选，来自环境变量） */
export const TMAP_KEY = import.meta.env.VITE_TMAP_KEY || ''

/** 代理占位符是否已被替换（未替换说明不在 WorkBuddy 预览环境，需要自备 Key） */
export function proxyAvailable() {
  return !TMAP_SERVICE_HOST.includes('__WB_HTTP_PORT__')
}

let loadingPromise = null

/** 加载腾讯地图 GL JS */
export function loadTMap() {
  if (window.TMap) return Promise.resolve(window.TMap)
  if (loadingPromise) return loadingPromise

  if (!TMAP_KEY && !window._TMapSecurityConfig) {
    window._TMapSecurityConfig = { serviceHost: TMAP_SERVICE_HOST }
  }

  const base = 'https://map.qq.com/api/gljs?v=1.exp&libraries=service'
  const url = TMAP_KEY ? `${base}&key=${encodeURIComponent(TMAP_KEY)}` : base

  loadingPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = url
    script.async = true
    script.onerror = () => reject(new Error('地图 SDK 加载失败，请检查网络'))
    document.head.appendChild(script)

    // 腾讯 GL SDK 的 window.TMap 并不保证在 onload 时就绪（内部还要异步初始化服务模块），
    // 因此这里轮询等待，避免偶发地把「就绪稍慢」误判为「SDK 缺失」。
    const started = Date.now()
    const timer = setInterval(() => {
      if (window.TMap && typeof window.TMap.Map === 'function') {
        clearInterval(timer)
        resolve(window.TMap)
      } else if (Date.now() - started > 15000) {
        clearInterval(timer)
        reject(new Error('地图 SDK 初始化超时，请检查 Key 与网络'))
      }
    }, 200)
  })
  return loadingPromise
}

/** 把 SDK 返回的位置对象统一成 { lat, lng } */
function toLatLng(location) {
  if (!location) return null
  const lat = typeof location.lat === 'function' ? location.lat() : location.lat
  const lng = typeof location.lng === 'function' ? location.lng() : location.lng
  if (typeof lat === 'number' && typeof lng === 'number' && !Number.isNaN(lat) && !Number.isNaN(lng)) {
    return { lat, lng }
  }
  return null
}

/** 地理编码：地点名 -> 经纬度，失败返回 null */
export function geocode(TMap, address) {
  return new Promise((resolve) => {
    try {
      const geocoder = new TMap.service.Geocoder()
      const ret = geocoder.getLocation({ address })
      Promise.resolve(ret)
        .then((res) => {
          noteServiceError(res)
          resolve(toLatLng(res && res.result && res.result.location))
        })
        .catch(() => resolve(null))
    } catch (e) {
      resolve(null)
    }
  })
}

/* ------------------------------------------------------------------
 * 坐标系解析兜底链
 * 腾讯位置服务对「地理编码(geocoder)」与「关键词提示(suggestion)/地点搜索(search)」
 * 是分开计量的。实际使用中常见 geocoder 配额为 0 或已用尽（status=121），
 * 导致所有点位都解析不到坐标、地图上一片空白。
 * 因此这里做三级兜底：geocoder -> suggestion -> search。
 * ------------------------------------------------------------------ */

/** 最近一次腾讯服务返回的错误（如 121=配额用尽、110=来源未授权），供界面提示用 */
export const lastServiceError = { code: null, message: '' }

/**
 * 被腾讯判定为「不可用」的接口（配额为 0 / 已用尽 / Key 未开通该接口）。
 * 一旦确认，本次会话内不再尝试它——否则每个地点都要白等一次失败的请求。
 */
const DISABLED = { geocoder: false, suggestion: false, search: false }

/** 这些状态码代表「这个接口暂时别用了」，属于配置/额度问题，重试也不会好 */
const FATAL_STATUS = { 110: true, 120: true, 121: true, 301: true }

/** 重置接口可用状态（用户主动点「重新解析地点」时调用，给他一次重新尝试的机会） */
export function resetServiceState() {
  DISABLED.geocoder = false
  DISABLED.suggestion = false
  DISABLED.search = false
}

/** 当前被跳过的接口名，供界面提示用 */
export function disabledInterfaces() {
  return Object.keys(DISABLED).filter((k) => DISABLED[k])
}

/** @returns {boolean} 是否是一个业务错误响应 */
function noteServiceError(res, which) {
  if (res && typeof res.status === 'number' && res.status !== 0) {
    lastServiceError.code = res.status
    lastServiceError.message = res.message || ''
    if (which && FATAL_STATUS[res.status]) DISABLED[which] = true
    return true
  }
  return false
}

/** 从 service 返回的数组里取第一个可用坐标 */
function firstLocation(list) {
  if (!Array.isArray(list)) return null
  for (const it of list) {
    const pos = toLatLng(it && it.location)
    if (pos) return pos
  }
  return null
}

function tryGeocode(TMap, address) {
  return new Promise((resolve) => {
    try {
      const ret = new TMap.service.Geocoder().getLocation({ address })
      Promise.resolve(ret)
        .then((res) => resolve({ pos: toLatLng(res && res.result && res.result.location), err: noteServiceError(res, 'geocoder') }))
        .catch(() => resolve({ pos: null, err: false }))
    } catch (e) {
      resolve({ pos: null, err: false })
    }
  })
}

function trySuggest(TMap, keyword, region) {
  return new Promise((resolve) => {
    try {
      // 注意：Suggestion 的 region 要在构造函数里传，getSuggestions 的入参只认 keyword/location
      const svc = new TMap.service.Suggestion({ pageSize: 5, region: region || '' })
      Promise.resolve(svc.getSuggestions({ keyword }))
        .then((res) => resolve({ pos: firstLocation(res && res.data), err: noteServiceError(res, 'suggestion') }))
        .catch(() => resolve({ pos: null, err: false }))
    } catch (e) {
      resolve({ pos: null, err: false })
    }
  })
}

function trySearch(TMap, keyword, region) {
  return new Promise((resolve) => {
    try {
      // 注意：Search 类没有 search()，按区域检索要用 searchRegion({keyword, cityName})
      const svc = new TMap.service.Search({ pageSize: 5 })
      const opt = { keyword, cityName: region || '' }
      Promise.resolve(svc.searchRegion(opt))
        .then((res) => resolve({ pos: firstLocation(res && res.data), err: noteServiceError(res) }))
        .catch(() => resolve({ pos: null, err: false }))
    } catch (e) {
      resolve({ pos: null, err: false })
    }
  })
}

/**
 * 解析地点坐标，按 geocoder -> suggestion -> search 依次尝试。
 * @param {object} TMap  TMap 命名空间
 * @param {string} name  地点名
 * @param {string} city  所属城市（用于缩小检索范围）
 * @returns {Promise<{pos: {lat:number,lng:number}|null, via: string|null}>}
 */
const CALLERS = {
  geocoder: (TMap, kw, city) => tryGeocode(TMap, city ? `${city}${kw}` : kw),
  suggestion: (TMap, kw, city) => trySuggest(TMap, kw, city),
  search: (TMap, kw, city) => trySearch(TMap, city ? `${city}${kw}` : kw, city)
}

export async function locate(TMap, name, city) {
  const kw = String(name || '').trim()
  if (!kw) return { pos: null, via: null }

  const order = ['geocoder', 'suggestion', 'search']
  const usable = order.filter((m) => !DISABLED[m])
  // 三个都被判定不可用时，仍按原顺序试一遍，避免「全禁用」导致一个点都定位不了
  const chain = usable.length ? usable : order

  for (const m of chain) {
    const r = await CALLERS[m](TMap, kw, city)
    if (r.pos) return { pos: r.pos, via: m }
  }
  return { pos: null, via: null }
}

/* ------------------------------------------------------------------
 * 地点搜索（供地图上「搜索酒店位置」使用）
 * 与坐标解析是两条独立链路：这里要的是「列表 + 地址 + 电话」，
 * 用于让用户从真实 POI 中挑选住宿位置。
 * ------------------------------------------------------------------ */

/** 把 Search / Suggestion 返回的数组统一成前端列表结构 */
function normalizePlaces(list) {
  if (!Array.isArray(list)) return []
  const out = []
  for (const it of list) {
    const pos = toLatLng(it && it.location)
    if (!pos) continue
    out.push({
      id: String(it.id || it.title + '_' + pos.lat + '_' + pos.lng),
      name: String(it.title || '').trim(),
      address: String(it.address || (it.ad_info && it.ad_info.address) || '').trim(),
      tel: String(it.tel || '').trim(),
      category: String(it.category || '').trim(),
      lat: pos.lat,
      lng: pos.lng
    })
  }
  return out.filter((p) => p.name)
}

/**
 * 按关键词在城市范围内搜索地点（默认搜「酒店」）。
 * 优先地点搜索（字段全，带地址/电话），失败再退到关键词提示（字段少但可用）。
 * 配额类错误会原样带回 status，交由界面提示，而不是静默返回空列表。
 *
 * @returns {Promise<{list: Array, error: {status:number,message:string}|null}>}
 */
export async function searchPlaces(TMap, keyword, city, pageSize = 10) {
  const kw = String(keyword || '').trim() || '酒店'
  if (!TMap || !TMap.service) {
    return { list: [], error: { status: -1, message: '地图服务尚未就绪' } }
  }

  const viaSearch = await new Promise((resolve) => {
    try {
      const svc = new TMap.service.Search({ pageSize })
      Promise.resolve(svc.searchRegion({ keyword: kw, cityName: city || '' }))
        .then((res) => {
          if (noteServiceError(res)) {
            resolve({ list: [], error: { status: res.status, message: res.message || '' } })
            return
          }
          resolve({ list: normalizePlaces(res && res.data), error: null })
        })
        .catch((e) => resolve({ list: [], error: { status: -1, message: (e && e.message) || '搜索失败' } }))
    } catch (e) {
      resolve({ list: [], error: { status: -1, message: (e && e.message) || '搜索不可用' } })
    }
  })
  if (viaSearch.list.length) return viaSearch

  const viaSuggest = await new Promise((resolve) => {
    try {
      const svc = new TMap.service.Suggestion({ pageSize, region: city || '' })
      Promise.resolve(svc.getSuggestions({ keyword: kw }))
        .then((res) => {
          if (noteServiceError(res)) {
            resolve({ list: [], error: { status: res.status, message: res.message || '' } })
            return
          }
          resolve({ list: normalizePlaces(res && res.data), error: null })
        })
        .catch(() => resolve({ list: [], error: viaSearch.error }))
    } catch (e) {
      resolve({ list: [], error: viaSearch.error })
    }
  })
  if (viaSuggest.list.length) return viaSuggest
  return { list: [], error: viaSuggest.error || viaSearch.error }
}

/**
 * 逆地理编码：经纬度 -> 名称 / 地址。
 * 注意：即使逆解析失败（地理编码配额为 0 很常见），也仍然返回坐标本身，
 * 保证「在地图上点选位置」这个操作永远可用，只是名字退化成坐标。
 */
export function reverseGeocode(TMap, lat, lng) {
  return new Promise((resolve) => {
    const fallback = {
      name: `${lat.toFixed(5)}, ${lng.toFixed(5)}`,
      address: '（未能解析出地址，可后续手动改名）',
      lat,
      lng
    }
    try {
      const geo = new TMap.service.Geocoder()
      Promise.resolve(geo.getAddress({ location: new TMap.LatLng(lat, lng) }))
        .then((res) => {
          if (noteServiceError(res, 'geocoder')) {
            resolve(fallback)
            return
          }
          const r = (res && res.result) || {}
          const addr = String(r.address || '').trim()
          const rec =
            (r.formatted_addresses && (r.formatted_addresses.recommend || r.formatted_addresses.rough)) || ''
          resolve({
            name: String(rec || addr || fallback.name).trim(),
            address: addr || fallback.address,
            lat,
            lng
          })
        })
        .catch(() => resolve(fallback))
    } catch (e) {
      resolve(fallback)
    }
  })
}

/**
 * 常见城市中心坐标（GCJ-02），仅在地理编码不可用或失败时兜底使用，
 * 用于保证地图仍能定位到城市大致范围。
 */
const CITY_CENTER = {
  北京: [39.9042, 116.4074], 上海: [31.2304, 121.4737], 广州: [23.1291, 113.2644],
  深圳: [22.5431, 114.0579], 成都: [30.5728, 104.0668], 重庆: [29.5630, 106.5516],
  杭州: [30.2741, 120.1551], 西安: [34.3416, 108.9398], 南京: [32.0603, 118.7969],
  武汉: [30.5928, 114.3055], 长沙: [28.2282, 112.9388], 厦门: [24.4798, 118.0894],
  青岛: [36.0671, 120.3826], 大理: [25.6065, 100.2676], 丽江: [26.8721, 100.2299],
  三亚: [18.2528, 109.5119], 桂林: [25.2736, 110.2900], 苏州: [31.2989, 120.5853],
  天津: [39.3434, 117.3616], 东莞: [23.0207, 113.7518], 昆明: [25.0389, 102.7183],
  贵阳: [26.6470, 106.6302], 拉萨: [29.6520, 91.1721], 哈尔滨: [45.8038, 126.5349],
  沈阳: [41.8057, 123.4315], 郑州: [34.7466, 113.6254], 济南: [36.6512, 117.1201],
  福州: [26.0745, 119.2965], 合肥: [31.8206, 117.2272], 南昌: [28.6820, 115.8579],
  太原: [37.8706, 112.5489], 兰州: [36.0611, 103.8343], 呼和浩特: [40.8414, 111.7519],
  乌鲁木齐: [43.8256, 87.6168], 海口: [20.0444, 110.1999], 香港: [22.3193, 114.1694],
  澳门: [22.1987, 113.5438], 台北: [25.0330, 121.5654]
}

/** 城市名兜底坐标 */
export function cityCenter(cityName) {
  if (!cityName) return null
  const key = String(cityName).replace(/市$|省$|特别行政区$|自治州$|地区$/g, '')
  if (CITY_CENTER[key]) {
    const [lat, lng] = CITY_CENTER[key]
    return { lat, lng }
  }
  for (const name of Object.keys(CITY_CENTER)) {
    if (key.includes(name) || name.includes(key)) {
      const [lat, lng] = CITY_CENTER[name]
      return { lat, lng }
    }
  }
  return null
}

/** 序号气泡图标（内联 SVG，不引用外部示例图） */
export function pinDataUri(color, text) {
  const svg =
    '<svg xmlns="http://www.w3.org/2000/svg" width="28" height="36" viewBox="0 0 28 36">' +
    '<path d="M14 0C6.3 0 0 6.3 0 14c0 10.5 14 22 14 22s14-11.5 14-22C28 6.3 21.7 0 14 0z" fill="' +
    color + '"/>' +
    '<circle cx="14" cy="14" r="9" fill="#ffffff"/>' +
    '<text x="14" y="18.3" font-size="11" font-weight="bold" text-anchor="middle" ' +
    'font-family="Arial,Microsoft YaHei" fill="' + color + '">' + text + '</text></svg>'
  return 'data:image/svg+xml,' + encodeURIComponent(svg)
}

export const DAY_COLORS = [
  '#2f6fed', '#f2994a', '#27ae60', '#eb5757', '#9b51e0',
  '#00b8d9', '#e67e22', '#16a085', '#d6336c', '#5c7cfa'
]
