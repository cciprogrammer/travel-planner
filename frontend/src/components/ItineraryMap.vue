<template>
  <div class="card">
    <div class="map-head no-print">
      <h3 class="sec-title">行程地图</h3>
      <div class="map-ops">
        <el-radio-group v-if="dayCount" v-model="activeDay" size="small">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button v-for="d in dayCount" :key="d" :value="d">第 {{ d }} 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :icon="Refresh" :loading="status === 'loading'" @click="init">
          重新解析地点
        </el-button>
      </div>
    </div>

    <!-- 搜索酒店位置：结果可直接设为住宿位置；也可直接在地图上点选 -->
    <div class="map-search no-print">
      <el-input v-model="searchKeyword" :prefix-icon="Search" clearable class="search-input"
        :disabled="status !== 'ready'" :placeholder="searchPlaceholder" @keyup.enter="doSearch" />
      <el-button type="primary" :icon="Search" :loading="searching" :disabled="status !== 'ready'"
        @click="doSearch">搜索酒店</el-button>
      <el-button :type="picking ? 'warning' : 'default'" :icon="Aim" :disabled="status !== 'ready'"
        @click="togglePicking">{{ picking ? '点选中：点击地图' : '在地图上点选' }}</el-button>
    </div>

    <el-alert v-if="searchNote" :type="searchFailed ? 'warning' : 'info'" :closable="false" show-icon
      class="no-print" style="margin-bottom:10px">
      <template #title>{{ searchFailed ? '酒店搜索失败' : '没有搜到匹配地点' }}</template>
      {{ searchNote }}
      <div style="margin-top:4px">也可以点「在地图上点选」直接在地图上指定位置（不依赖搜索接口）。</div>
    </el-alert>

    <div v-if="searchResults.length" class="search-panel no-print">
      <div class="search-panel-head">
        <span>找到 {{ searchResults.length }} 个地点 · 点任意一条即可设为住宿位置</span>
        <el-button link size="small" @click="searchResults = []">收起</el-button>
      </div>
      <div class="search-list">
        <div v-for="r in searchResults" :key="r.id" class="search-item" @click="pickFromSearch(r)">
          <div class="si-line1">
            <strong>{{ r.name }}</strong>
            <el-tag v-if="r.category" size="small" effect="plain" type="info">{{ r.category }}</el-tag>
            <el-tag v-if="isCurrentPick(r)" size="small" effect="dark" type="warning">当前住宿</el-tag>
          </div>
          <div class="si-line2">
            {{ r.address || '暂无详细地址' }}<template v-if="r.tel"> ｜ {{ r.tel }}</template>
          </div>
        </div>
      </div>
    </div>

    <div v-if="pendingPick" class="pick-bar no-print">
      <el-icon><LocationFilled /></el-icon>
      <div class="pick-text">
        <div class="pick-name">{{ pendingPick.name }}</div>
        <div class="muted">{{ pendingPick.address }}</div>
      </div>
      <el-button size="small" type="primary" @click="confirmPending">设为住宿位置</el-button>
      <el-button size="small" @click="cancelPending">取消</el-button>
    </div>

    <el-alert v-if="status === 'error'" type="warning" :closable="false" show-icon class="no-print">
      <template #title>地图暂不可用</template>
      {{ errorMsg }}
      <div v-if="errorNeedsKey" style="margin-top:6px">
        申请入口：腾讯位置服务开放平台 → 控制台 → 创建应用 → 添加 Key（WebService / JavaScript GL），
        将 Key 写入 <code>frontend/.env</code> 的 <code>VITE_TMAP_KEY</code> 后重启前端。
      </div>
      <div v-else style="margin-top:6px">
        可点右上角「重新解析地点」重试；若持续失败，请打开浏览器控制台查看详细错误。
      </div>
    </el-alert>

    <!-- 地图容器必须始终有真实尺寸：TMap GL 在 display:none 的容器上初始化会抛 "far <= 0" -->
    <div class="map-wrap">
      <div v-show="status !== 'error'" ref="mapEl" class="map-container"></div>
      <div v-if="status === 'loading'" class="map-mask">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在加载地图并解析地点…</span>
      </div>
    </div>

    <div v-if="status === 'ready'" class="map-foot">
      <div class="legend">
        <span v-for="d in dayCount" :key="d" class="legend-item">
          <i :style="{ background: dayColor(d - 1) }"></i>第 {{ d }} 天
        </span>
        <span v-if="hotelPoint" class="legend-item">
          <i :style="{ background: HOTEL_COLOR }"></i>住宿（{{ hotelPoint.name }}）
        </span>
      </div>
      <span class="muted">
        共解析 {{ points.length }} 个地点<template v-if="hotelPoint">、1 个住宿点</template>
        <template v-if="failedCount">，{{ failedCount }} 个地点未能定位（可手动编辑地点名称后重试）</template>
      </span>
    </div>

    <el-alert v-if="status === 'ready' && customLocation" type="success" :closable="false" show-icon
      class="no-print" style="margin-top:10px">
      <template #title>住宿位置已按你的选择重排路线</template>
      「{{ customLocation.name }}」已作为每天的出发点与返回点，{{ dayCount }} 天的动线全部按这个位置重新连线。
      <el-button link type="primary" size="small" @click="$emit('clear-hotel')">恢复为推荐酒店位置</el-button>
    </el-alert>

    <el-alert v-if="status === 'ready' && fallbackCount" type="info" :closable="false" class="no-print" style="margin-top:10px">
      <template #title>{{ geoDisabled ? '已启用备用定位方式' : '部分地点使用了备用定位方式' }}</template>
      <template v-if="geoDisabled">
        其中 {{ fallbackCount }} 个地点是通过「关键词提示 / 地点搜索」定位的。
        当前 Key 的地理编码接口不可用（常见原因：该接口配额为 0 或当日已用尽），
        可到腾讯位置服务控制台为 Key 开通或提升「地理编码」配额。
      </template>
      <template v-else>
        其中 {{ fallbackCount }} 个地点的地址解析没命中，已改用关键词检索匹配，位置可能略有偏差。
      </template>
    </el-alert>

    <el-alert v-if="status === 'ready' && !points.length && failedCount" type="warning" :closable="false" class="no-print" style="margin-top:10px">
      <template #title>地点未能定位</template>
      共 {{ failedCount }} 个地点解析失败{{ serviceHint }}。
      常见原因是该 Key 的「地理编码 / 关键词提示 / 地点搜索」配额为 0 或当日已用尽，
      可到腾讯位置服务控制台检查配额，稍后点右上角「重新解析地点」重试。
    </el-alert>

    <!-- 出行方案：连线是直线示意，每段「怎么走」用文字说明 -->
    <div v-if="status === 'ready' && dayRoutes.length" class="route-plan no-print">
      <div class="rp-head">
        <h4 class="rp-title-main">出行方案</h4>
        <span class="muted">
          连线为直线示意（不按真实道路），每段怎么走见下方说明；点地图上的连线也能查看当天方案。
        </span>
      </div>
      <div v-for="r in dayRoutes" :key="r.day" class="rp-day">
        <div class="rp-day-head">
          <i :style="{ background: dayColor(r.dayIndex) }"></i>
          <strong>第 {{ r.day }} 天</strong>
          <span v-if="r.theme" class="muted">· {{ r.theme }}</span>
        </div>
        <div v-if="r.dayTransport" class="rp-day-sum">{{ r.dayTransport }}</div>
        <ol class="rp-legs">
          <li v-for="(leg, li) in r.legs" :key="li">
            <span class="rp-point">{{ leg.from }}</span>
            <span class="rp-arrow">→</span>
            <span class="rp-point">{{ leg.to }}</span>
            <span class="rp-how">{{ leg.how || '交通方式未填写，可在「编辑方案」里补充' }}</span>
          </li>
        </ol>
      </div>
    </div>

    <el-empty v-if="status === 'ready' && !points.length && !failedCount" description="该方案暂未包含可定位的地点" :image-size="70" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { Refresh, Loading, Search, Aim, LocationFilled } from '@element-plus/icons-vue'
import {
  loadTMap, locate, cityCenter, pinDataUri, DAY_COLORS,
  TMAP_KEY, proxyAvailable, lastServiceError, resetServiceState, disabledInterfaces,
  searchPlaces, reverseGeocode
} from '@/utils/map'

const props = defineProps({
  plan: { type: Object, default: () => ({}) }
})

/** 把「用户在地图上指定的住宿位置」交给父组件写回方案，父组件负责持久化 */
const emit = defineEmits(['pick-hotel', 'clear-hotel'])

const mapEl = ref(null)
const status = ref('idle') // idle | loading | ready | error
const errorMsg = ref('')
const points = ref([])
const failedCount = ref(0)
const fallbackCount = ref(0)
const activeDay = ref(null)
const serviceHint = ref('')
/** 地理编码接口是否被腾讯判定为不可用（配额为 0 / 已用尽） */
const geoDisabled = ref(false)
/** 所选住宿的坐标：作为每天动线的出发与返回点 */
const hotelPoint = ref(null)

/** 住宿节点配色（与每日颜色区分） */
const HOTEL_COLOR = '#8957e5'

/* ------------------------ 搜索酒店 / 地图点选住宿位置 ------------------------ */

const searchKeyword = ref('')
const searching = ref(false)
const searchResults = ref([])
/** 搜索提示文案（失败原因或无结果提示） */
const searchNote = ref('')
const searchFailed = ref(false)
/** 是否处于「在地图上点选」模式 */
const picking = ref(false)
/** 地图点选后待确认的位置 */
const pendingPick = ref(null)

/** 搜索范围：优先目的地城市 */
const searchCity = computed(() => (props.plan && (props.plan.destination || props.plan.origin)) || '')

const searchPlaceholder = computed(() =>
  searchCity.value ? `搜索${searchCity.value}的酒店，如：亚朵 / 五一广场` : '搜索酒店名称'
)

/** 用户在地图上指定的住宿位置（为空表示仍用推荐酒店） */
const customLocation = computed(() => {
  const s = props.plan && props.plan.itinerary && props.plan.itinerary.stay
  return (s && s.customLocation) || null
})

/** 把腾讯服务返回的错误码翻译成一句人话，拼到提示语后面 */
const SERVICE_ERROR_HINT = {
  110: '（腾讯返回 110：请求来源未被授权，请检查 Key 的授权域名/Referer）',
  111: '（腾讯返回 111：签名校验失败）',
  120: '（腾讯返回 120：此 Key 未开通该接口）',
  121: '（腾讯返回 121：该接口今日调用量已达上限）',
  301: '（腾讯返回 301：Key 无效或已过期）'
}

let TMapRef = null
let map = null
let markerLayer = null
let lineLayer = null
let infoWindow = null
/** 地图点选时的临时标记 */
let pickMarker = null

const days = computed(() => (props.plan && props.plan.itinerary && props.plan.itinerary.days) || [])
const dayCount = computed(() => days.value.length)

/**
 * 每日出行方案：把「住宿点 → 当天各景点 → 住宿点」拆成一段一段，
 * 每段带上该景点的交通说明（item.transport）。
 * 折线本身仍是直线示意，不调用路径规划接口，但每一段「怎么走」都有文字交代。
 */
const dayRoutes = computed(() => {
  const list = []
  const hp = hotelPoint.value
  days.value.forEach((day, di) => {
    if (activeDay.value !== null && activeDay.value - 1 !== di) return
    const ps = points.value
      .filter((p) => p.dayIndex === di)
      .sort((a, b) => a.order - b.order)
    if (!ps.length) return
    const legs = []
    if (hp) legs.push({ from: hp.name, to: ps[0].place, how: ps[0].transport || '' })
    for (let i = 1; i < ps.length; i++) {
      legs.push({ from: ps[i - 1].place, to: ps[i].place, how: ps[i].transport || '' })
    }
    if (hp) {
      legs.push({ from: ps[ps.length - 1].place, to: hp.name, how: '返回住宿点，可原路返回或打车' })
    }
    list.push({
      day: day.day || di + 1,
      dayIndex: di,
      theme: day.theme || '',
      dayTransport: day.transport || '',
      legs
    })
  })
  return list
})

function dayColor(index) {
  return DAY_COLORS[index % DAY_COLORS.length]
}

/** 失败原因是否与 Key 有关——决定错误提示里要不要引导去申请 Key */
const errorNeedsKey = computed(() => {
  if (!TMAP_KEY) return true
  return /Key|SDK 加载失败|SDK 初始化超时/.test(errorMsg.value || '')
})

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

/** 销毁已有地图实例与图层，保证重新解析时是干净状态 */
function destroyMap() {
  try {
    if (infoWindow) { infoWindow.close(); infoWindow = null }
    if (pickMarker) { pickMarker.setMap(null); pickMarker = null }
    pendingPick.value = null
    if (markerLayer) { markerLayer.setMap(null); markerLayer = null }
    if (lineLayer) { lineLayer.setMap(null); lineLayer = null }
    if (map) { map.destroy(); map = null }
  } catch (e) {
    console.warn('[ItineraryMap] 清理旧地图时出错：', e)
  }
  // SDK 销毁后容器里可能残留节点，清掉避免叠加
  if (mapEl.value) mapEl.value.innerHTML = ''
}

/**
 * 等待容器拥有真实尺寸。
 * TMap GL 会按容器宽高初始化 WebGL 相机，容器为 0×0（例如被 v-show 隐藏）时
 * 会抛出 "far <= 0"，表现为「地图加载失败」。
 */
async function waitContainerReady(el, tries = 40) {
  for (let i = 0; i < tries; i++) {
    if (el && el.clientWidth > 0 && el.clientHeight > 0) return true
    await sleep(50)
  }
  return false
}

async function init() {
  if (!TMAP_KEY && !proxyAvailable()) {
    status.value = 'error'
    errorMsg.value = '当前环境未配置腾讯地图 Key。'
    return
  }
  status.value = 'loading'
  errorMsg.value = ''
  failedCount.value = 0
  fallbackCount.value = 0
  serviceHint.value = ''
  try {
    TMapRef = await loadTMap()
    const city = (props.plan && (props.plan.destination || props.plan.origin)) || ''
    let center = (await locate(TMapRef, city, city)).pos
    if (!center) center = cityCenter(city) || { lat: 35.0, lng: 105.0 }

    await nextTick()
    const sized = await waitContainerReady(mapEl.value)
    if (!sized) throw new Error('地图容器尺寸为 0，请确认页面已正常显示后再试')

    destroyMap()
    map = new TMapRef.Map(mapEl.value, {
      center: new TMapRef.LatLng(center.lat, center.lng),
      zoom: 12,
      pitch: 0,
      showControl: true
    })
    infoWindow = new TMapRef.InfoWindow({
      map,
      position: new TMapRef.LatLng(center.lat, center.lng),
      content: '',
      offset: { x: 0, y: -32 }
    })
    infoWindow.close()
    // 点选模式下，单击地图落一个候选住宿点
    map.on('click', onMapClick)

    await resolvePoints(city)
    await resolveHotel(city)
    status.value = 'ready'
    await nextTick()
    render()
  } catch (e) {
    status.value = 'error'
    // SDK 内部有些异常是普通对象（没有 message），这里尽量取出可读信息并落控制台
    const detail = e && (e.message || e.msg || e.info)
    errorMsg.value = detail || String(e || '') || '地图加载失败（未知错误，详见控制台）'
    window.__mapInitError = e
    console.error('[ItineraryMap] 地图初始化失败：', e)
  }
}

async function resolvePoints(city) {
  const list = []
  let failed = 0
  let total = 0
  let fallback = 0
  lastServiceError.code = null
  lastServiceError.message = ''
  // 用户主动点「重新解析地点」时，给之前被判死的接口一次重新尝试的机会
  resetServiceState()
  for (let di = 0; di < days.value.length; di++) {
    const items = days.value[di].items || []
    for (let ii = 0; ii < items.length; ii++) {
      const item = items[ii]
      const name = String(item.place || '').trim()
      if (!name) continue
      if (total >= 40) break
      total++
      const { pos, via } = await locate(TMapRef, name, city)
      if (pos) {
        if (via && via !== 'geocoder') fallback++
        list.push({
          id: `p_${di}_${ii}`,
          dayIndex: di,
          order: ii + 1,
          day: days.value[di].day || di + 1,
          theme: days.value[di].theme || '',
          place: name,
          time: item.time || '',
          activity: item.activity || '',
          /** 从上一地点到这里的交通方式与耗时，用于生成「出行方案」 */
          transport: item.transport || '',
          cost: item.cost || '',
          tips: item.tips || '',
          lat: pos.lat,
          lng: pos.lng
        })
      } else {
        failed++
      }
      await sleep(80)
    }
  }
  points.value = list
  failedCount.value = failed
  fallbackCount.value = fallback
  geoDisabled.value = disabledInterfaces().includes('geocoder')
  if (failed) {
    const code = lastServiceError.code
    serviceHint.value = code
      ? (SERVICE_ERROR_HINT[code] || `（腾讯返回 ${code}：${lastServiceError.message || '接口调用失败'}）`)
      : ''
  }
}

/**
 * 解析「当前选中的酒店」坐标。
 * 用户切换酒店后，这里会重新定位，随后 render() 把该酒店作为每天动线的首尾节点重画。
 */
async function resolveHotel(city) {
  hotelPoint.value = null
  const it = (props.plan && props.plan.itinerary) || {}

  // ① 用户在地图上指定过位置 → 直接用该坐标，不再走地理编码
  const custom = it.stay && it.stay.customLocation
  if (custom && Number.isFinite(Number(custom.lat)) && Number.isFinite(Number(custom.lng))) {
    hotelPoint.value = {
      id: 'hotel',
      name: custom.name || '地图选点',
      area: custom.address || '',
      price: '',
      level: '',
      custom: true,
      lat: Number(custom.lat),
      lng: Number(custom.lng)
    }
    return
  }

  // ② 否则按当前选中的推荐酒店名称解析坐标
  const name = it.stay && it.stay.selectedHotel
  if (!name || !TMapRef) return
  const hotel = (it.hotels || []).find((h) => h.name === name) || null
  const { pos } = await locate(TMapRef, name, city)
  if (!pos) return
  hotelPoint.value = {
    id: 'hotel',
    name,
    area: (hotel && hotel.area) || '',
    price: (hotel && hotel.price) || '',
    level: (hotel && hotel.level) || '',
    custom: false,
    lat: pos.lat,
    lng: pos.lng
  }
}

/* ------------------------ 搜索酒店 / 地图点选住宿位置 ------------------------ */

/** 某条搜索结果是否就是当前住宿位置 */
function isCurrentPick(r) {
  const c = customLocation.value
  if (!c) return false
  return Math.abs(Number(c.lat) - r.lat) < 1e-6 && Math.abs(Number(c.lng) - r.lng) < 1e-6
}

async function doSearch() {
  if (!TMapRef) return
  searching.value = true
  searchNote.value = ''
  searchFailed.value = false
  try {
    const { list, error } = await searchPlaces(TMapRef, searchKeyword.value, searchCity.value, 10)
    searchResults.value = list
    if (error) {
      searchFailed.value = true
      const code = error.status
      searchNote.value =
        code > 0
          ? SERVICE_ERROR_HINT[code] || `腾讯返回 ${code}：${error.message || '接口调用失败'}`
          : error.message || '搜索接口暂时不可用'
    } else if (!list.length) {
      searchNote.value = `没有在「${searchCity.value || '当前城市'}」搜到「${searchKeyword.value || '酒店'}」，换个关键词试试。`
    }
  } finally {
    searching.value = false
  }
}

function pickFromSearch(r) {
  emit('pick-hotel', {
    name: r.name,
    address: r.address,
    lat: r.lat,
    lng: r.lng,
    source: 'search'
  })
}

function applyCursor() {
  if (mapEl.value) mapEl.value.style.cursor = picking.value ? 'crosshair' : ''
}

function togglePicking() {
  picking.value = !picking.value
  cancelPending()
  applyCursor()
}

/** 地图单击：进入点选模式后落一个待确认的点 */
async function onMapClick(evt) {
  if (!picking.value || !TMapRef) return
  const loc = evt && evt.latLng
  if (!loc) return
  const lat = typeof loc.lat === 'function' ? loc.lat() : loc.lat
  const lng = typeof loc.lng === 'function' ? loc.lng() : loc.lng
  if (typeof lat !== 'number' || typeof lng !== 'number') return

  pendingPick.value = { name: '正在解析地址…', address: '', lat, lng, source: 'map' }
  renderPending()
  const info = await reverseGeocode(TMapRef, lat, lng)
  pendingPick.value = { ...info, source: 'map' }
}

/** 待确认点的临时标记 */
function renderPending() {
  if (!map || !TMapRef) return
  if (pickMarker) {
    pickMarker.setMap(null)
    pickMarker = null
  }
  const p = pendingPick.value
  if (!p) return
  const styles = {
    s_pick: new TMapRef.MarkerStyle({
      width: 28,
      height: 36,
      anchor: { x: 14, y: 34 },
      src: pinDataUri('#e6a23c', '选')
    })
  }
  pickMarker = new TMapRef.MultiMarker({
    map,
    styles,
    geometries: [{ id: 'pick', styleId: 's_pick', position: new TMapRef.LatLng(p.lat, p.lng) }]
  })
}

function confirmPending() {
  const p = pendingPick.value
  if (!p) return
  emit('pick-hotel', { name: p.name, address: p.address, lat: p.lat, lng: p.lng, source: 'map' })
  pendingPick.value = null
  picking.value = false
  applyCursor()
  if (pickMarker) {
    pickMarker.setMap(null)
    pickMarker = null
  }
}

function cancelPending() {
  pendingPick.value = null
  if (pickMarker) {
    pickMarker.setMap(null)
    pickMarker = null
  }
}

function render() {
  if (!map || !TMapRef) return

  const visible = points.value.filter(
    (p) => activeDay.value === null || p.dayIndex === activeDay.value - 1
  )

  // 清理旧图层
  if (markerLayer) {
    markerLayer.setMap(null)
    markerLayer = null
  }
  if (lineLayer) {
    lineLayer.setMap(null)
    lineLayer = null
  }
  infoWindow && infoWindow.close()
  if (!visible.length && !hotelPoint.value) return

  // 标记（景点 + 住宿节点）
  const styles = {}
  const geometries = []
  visible.forEach((p) => {
    const color = dayColor(p.dayIndex)
    const label = activeDay.value === null ? String(p.day) : String(p.order)
    const styleId = 's_' + p.id
    styles[styleId] = new TMapRef.MarkerStyle({
      width: 28,
      height: 36,
      anchor: { x: 14, y: 34 },
      src: pinDataUri(color, label)
    })
    geometries.push({
      id: p.id,
      styleId,
      position: new TMapRef.LatLng(p.lat, p.lng)
    })
  })
  if (hotelPoint.value) {
    styles['s_hotel'] = new TMapRef.MarkerStyle({
      width: 28,
      height: 36,
      anchor: { x: 14, y: 34 },
      src: pinDataUri(HOTEL_COLOR, '宿')
    })
    geometries.push({
      id: 'hotel',
      styleId: 's_hotel',
      position: new TMapRef.LatLng(hotelPoint.value.lat, hotelPoint.value.lng)
    })
  }
  markerLayer = new TMapRef.MultiMarker({ map, styles, geometries })
  markerLayer.on('click', showInfo)

  // 按日连线：选定住宿后，动线为「酒店 → 当天各景点 → 酒店」
  const lineStyles = {}
  const lineGeometries = []
  const hotelLatLng = hotelPoint.value
    ? new TMapRef.LatLng(hotelPoint.value.lat, hotelPoint.value.lng)
    : null
  const routeDebug = []
  days.value.forEach((day, di) => {
    if (activeDay.value !== null && activeDay.value - 1 !== di) return
    const nums = visible
      .filter((p) => p.dayIndex === di)
      .sort((a, b) => a.order - b.order)
      .map((p) => [p.lat, p.lng])
    const dayPath = nums.map((n) => new TMapRef.LatLng(n[0], n[1]))
    if (!dayPath.length) return
    const path = hotelLatLng ? [hotelLatLng, ...dayPath, hotelLatLng] : dayPath
    routeDebug.push({
      day: di + 1,
      start: hotelLatLng && hotelPoint.value ? [hotelPoint.value.lat, hotelPoint.value.lng] : nums[0],
      end: hotelLatLng && hotelPoint.value ? [hotelPoint.value.lat, hotelPoint.value.lng] : nums[nums.length - 1],
      waypoints: nums
    })
    if (path.length < 2) return
    const styleId = 'line_' + di
    const color = dayColor(di)
    lineStyles[styleId] = new TMapRef.PolylineStyle({
      color,
      width: 5,
      borderWidth: 1,
      borderColor: '#ffffff',
      lineCap: 'round'
    })
    lineGeometries.push({ id: styleId, styleId, paths: path })
  })
  if (lineGeometries.length) {
    lineLayer = new TMapRef.MultiPolyline({ map, styles: lineStyles, geometries: lineGeometries })
    // 点连线看当天出行方案（连线 id 形如 line_0）
    lineLayer.on('click', showLineInfo)
  }

  // 便于排查（与 window.__mapInitError 同风格）：把本次渲染的住宿点与每天动线首尾挂到 window 上
  window.__mapDebug = {
    hotel: hotelPoint.value
      ? {
          name: hotelPoint.value.name,
          lat: hotelPoint.value.lat,
          lng: hotelPoint.value.lng,
          custom: !!hotelPoint.value.custom
        }
      : null,
    routes: routeDebug
  }

  fitBounds(hotelPoint.value ? [...visible, hotelPoint.value] : visible)
}

const esc = (s) =>
  String(s || '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))

function showInfo(evt) {
  if (!TMapRef) return
  const id = evt.geometry.id
  // 住宿节点：单独的信息窗
  if (id === 'hotel' && hotelPoint.value) {
    const h = hotelPoint.value
    const html =
      '<div style="min-width:180px;max-width:260px;font-size:13px;line-height:1.7">' +
      '<div style="font-weight:600;color:#7c3aed">' + (h.custom ? '自选住宿位置' : '当前住宿') + '</div>' +
      '<div style="font-weight:600;color:#1f2430">' + esc(h.name) + '</div>' +
      (h.area ? '<div style="color:#7a8290">' + esc(h.area) + '</div>' : '') +
      (h.price ? '<div style="color:#e06c00;font-weight:600">' + esc(h.price) + '</div>' : '') +
      (h.custom
        ? '<div style="color:#8a909c;font-size:12px">你在图上指定的位置，{{DAYS}}天动线均以此为起终点</div>'
            .replace('{{DAYS}}', String(dayCount.value))
        : '<div style="color:#8a909c;font-size:12px">每天从这里出发，行程结束返回此处</div>') +
      '</div>'
    infoWindow.setPosition(new TMapRef.LatLng(h.lat, h.lng))
    infoWindow.setContent(html)
    infoWindow.open()
    return
  }
  const p = points.value.find((x) => x.id === id)
  if (!p) return
  const html =
    '<div style="min-width:180px;max-width:260px;font-size:13px;line-height:1.7">' +
    '<div style="font-weight:600;color:#1f2430">' + esc(p.place) + '</div>' +
    '<div style="color:#7a8290">第 ' + p.day + ' 天 · ' + esc(p.time || '时间待定') + '</div>' +
    (p.activity ? '<div style="margin-top:4px">' + esc(p.activity) + '</div>' : '') +
    (p.cost ? '<div style="color:#e06c00;font-weight:600">' + esc(p.cost) + '</div>' : '') +
    (p.tips ? '<div style="color:#8a909c;font-size:12px">' + esc(p.tips) + '</div>' : '') +
    '</div>'
  infoWindow.setPosition(new TMapRef.LatLng(p.lat, p.lng))
  infoWindow.setContent(html)
  infoWindow.open()
}

/** 点击折线：弹出当天出行方案（连线是直线示意，方案以文字形式给出） */
function showLineInfo(evt) {
  if (!TMapRef || !evt) return
  const id = (evt.geometry && evt.geometry.id) || ''
  const m = /^line_(\d+)$/.exec(id)
  if (!m) return
  const di = Number(m[1])
  const r = dayRoutes.value.find((x) => x.dayIndex === di)
  if (!r) return
  const legHtml = r.legs
    .slice(0, 8)
    .map(
      (l) =>
        '<div style="margin-top:4px">' +
        esc(l.from) +
        ' <span style="color:#2f6fed">→</span> ' +
        esc(l.to) +
        (l.how
          ? '<div style="color:#8a909c;font-size:12px">' + esc(l.how) + '</div>'
          : '') +
        '</div>'
    )
    .join('')
  const html =
    '<div style="min-width:220px;max-width:300px;font-size:13px;line-height:1.6">' +
    '<div style="font-weight:600;color:' + dayColor(di) + '">第 ' + r.day + ' 天出行方案</div>' +
    (r.dayTransport
      ? '<div style="color:#5c6470;font-size:12px;margin-top:2px">' + esc(r.dayTransport) + '</div>'
      : '') +
    legHtml +
    '</div>'
  if (evt.latLng) infoWindow.setPosition(evt.latLng)
  infoWindow.setContent(html)
  infoWindow.open()
}

/** 根据点集自行计算中心与层级，避免依赖 fitBounds 的差异 */
function fitBounds(list) {
  if (!list.length || !TMapRef) return
  const lats = list.map((p) => p.lat)
  const lngs = list.map((p) => p.lng)
  const minLat = Math.min(...lats)
  const maxLat = Math.max(...lats)
  const minLng = Math.min(...lngs)
  const maxLng = Math.max(...lngs)
  map.setCenter(new TMapRef.LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2))
  const span = Math.max(maxLat - minLat, maxLng - minLng)
  const zoom = span > 5 ? 8 : span > 2 ? 9 : span > 1 ? 10 : span > 0.4 ? 11 : span > 0.15 ? 12 : span > 0.05 ? 14 : 15
  map.setZoom(zoom)
}

watch(activeDay, () => render())

watch(
  () => props.plan && props.plan.id,
  () => {
    points.value = []
    hotelPoint.value = null
    failedCount.value = 0
    activeDay.value = null
    init()
  }
)

// 切换酒店：重新定位住宿节点，并按新位置重排每天的动线
watch(
  () => {
    const it = (props.plan && props.plan.itinerary) || {}
    return (it.stay && it.stay.selectedHotel) || ''
  },
  async (name, oldName) => {
    if (!TMapRef || !name || name === oldName || status.value !== 'ready') return
    const city = (props.plan && (props.plan.destination || props.plan.origin)) || ''
    await resolveHotel(city)
    render()
  }
)

// 用户在地图上换住宿位置：住宿节点与所有天的路线都要跟着变
watch(customLocation, async (loc, old) => {
  const oldKey = old ? `${old.lat},${old.lng}` : ''
  const newKey = loc ? `${loc.lat},${loc.lng}` : ''
  if (oldKey === newKey) return
  if (!TMapRef || status.value !== 'ready') return
  const city = (props.plan && (props.plan.destination || props.plan.origin)) || ''
  await resolveHotel(city)
  render()
  if (loc && map) {
    map.setCenter(new TMapRef.LatLng(Number(loc.lat), Number(loc.lng)))
    map.setZoom(14)
  }
})

onMounted(() => {
  init()
})

onBeforeUnmount(() => {
  destroyMap()
})
</script>

<style scoped>
.map-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.sec-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.map-ops {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.map-search {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.search-input {
  flex: 1;
  min-width: 220px;
}

.search-panel {
  border: 1px solid #e9edf5;
  border-radius: 10px;
  margin-bottom: 12px;
  overflow: hidden;
}

.search-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f7f9fc;
  font-size: 12px;
  color: #5c6470;
}

.search-list {
  max-height: 210px;
  overflow-y: auto;
}

.search-item {
  padding: 9px 12px;
  border-top: 1px solid #f0f3f8;
  cursor: pointer;
  transition: background 0.15s;
}

.search-item:hover {
  background: #eaf1ff;
}

.si-line1 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.si-line2 {
  margin-top: 3px;
  font-size: 12px;
  color: #8a909c;
}

.pick-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 12px;
  border: 1px solid #f0d9b5;
  background: #fff8ec;
  border-radius: 10px;
  color: #8a6100;
}

.pick-text {
  flex: 1;
  min-width: 0;
}

.pick-name {
  font-size: 13px;
  font-weight: 600;
  color: #7a5300;
}

.map-wrap {
  position: relative;
}

.map-container {
  width: 100%;
  height: 420px;
  border-radius: 10px;
  overflow: hidden;
  background: #eef2f7;
}

.map-mask {
  position: absolute;
  inset: 0;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(238, 242, 247, 0.86);
  color: #5c6470;
  font-size: 13px;
  pointer-events: none;
}

.map-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.legend {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #5c6470;
}

.legend-item i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.route-plan {
  margin-top: 14px;
  border-top: 1px dashed #e6ebf3;
  padding-top: 12px;
}

.rp-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.rp-title-main {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.rp-day {
  border-left: 3px solid #eaf1ff;
  padding: 4px 0 8px 12px;
  margin-bottom: 10px;
}

.rp-day-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  margin-bottom: 4px;
}

.rp-day-head i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.rp-day-sum {
  font-size: 12px;
  color: #7a8290;
  line-height: 1.7;
  margin-bottom: 4px;
}

.rp-legs {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #4b5464;
  line-height: 1.9;
}

.rp-legs li {
  margin-bottom: 2px;
}

.rp-point {
  color: #1f2430;
}

.rp-arrow {
  color: #2f6fed;
  margin: 0 4px;
}

.rp-how {
  color: #8a909c;
  margin-left: 6px;
}
</style>
