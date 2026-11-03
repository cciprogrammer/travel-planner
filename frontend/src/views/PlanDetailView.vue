<template>
  <div class="page">
    <div v-if="loading" class="skeleton-wrap">
      <p class="sk-hint">正在规划行程，通常需要 10–40 秒…</p>
      <el-skeleton animated>
        <template #template>
          <div class="card sk-card">
            <el-skeleton-item variant="h3" style="width: 38%" />
            <el-skeleton-item variant="text" style="width: 78%; margin-top: 12px" />
            <div class="sk-metrics">
              <el-skeleton-item v-for="i in 4" :key="i" variant="text"
                style="width: 72px; height: 36px" />
            </div>
          </div>
          <div v-for="i in 3" :key="i" class="card sk-card">
            <el-skeleton-item variant="h3" style="width: 20%" />
            <el-skeleton-item variant="text" style="width: 92%; margin-top: 14px" />
            <el-skeleton-item variant="text" style="width: 70%; margin-top: 8px" />
          </div>
        </template>
      </el-skeleton>
    </div>

    <template v-else-if="plan">
      <!-- 顶部操作栏：主操作前置，删除收进「更多」 -->
      <div class="card no-print detail-bar">
        <div class="bar-left">
          <el-button link :icon="ArrowLeft" @click="$router.push('/plans')">返回列表</el-button>
          <span class="bar-divider"></span>
          <span class="bar-title">{{ plan.title }}</span>
        </div>
        <div class="bar-actions">
          <template v-if="!editing">
            <el-button type="primary" :icon="Edit" @click="startEdit">编辑方案</el-button>
            <el-button :icon="Download" @click="exportMd">导出</el-button>
            <el-button :icon="Printer" @click="printPage">打印</el-button>
            <el-button :icon="Refresh" :loading="regenLoading" @click="regenerate">重新生成</el-button>
            <el-dropdown trigger="click" @command="onBarCommand">
              <el-button :icon="MoreFilled" class="more-btn" />
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="delete">
                    <span class="danger-text">删除方案</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button type="success" :icon="Check" :loading="saving" @click="save">保存修改</el-button>
            <el-button @click="cancelEdit">取消</el-button>
          </template>
        </div>
      </div>

      <anchor-nav :items="anchors" />

      <!-- 编辑态提示：避免误以为已保存 -->
      <div v-if="editing" class="edit-banner no-print">
        <span>正在编辑，修改需点「保存修改」后生效</span>
      </div>

      <!-- 基本信息 + 关键数字摘要（用户最关心的 4 个数提到首屏） -->
      <div id="sec-overview" class="card">
        <template v-if="editing">
          <el-input v-model="editForm.title" size="large" placeholder="方案标题" />
          <el-input v-model="editForm.summary" type="textarea" :rows="3" style="margin-top:12px"
            placeholder="行程总览" />
        </template>
        <template v-else>
          <div class="overview-title-row">
            <h2 class="page-title">{{ plan.title }}</h2>
            <span v-if="itinerary.quality" class="quality-pill"
              :class="itinerary.quality.passed ? 'is-ok' : 'is-warn'">
              <span class="dot"></span>
              质检{{ itinerary.quality.passed ? '通过' : '未通过' }} · {{ itinerary.quality.score }}%
            </span>
          </div>
          <p class="page-desc">{{ plan.summary }}</p>

          <div class="metric-strip">
            <div class="metric-cell">
              <div class="metric-label">行程天数</div>
              <div class="metric-value num">{{ plan.days }} 天</div>
            </div>
            <div class="metric-cell">
              <div class="metric-label">总预算</div>
              <div class="metric-value num">¥{{ planBudget || '未设置' }}</div>
            </div>
            <div class="metric-cell">
              <div class="metric-label">住宿花销</div>
              <div class="metric-value num amount">¥{{ stayCost }}</div>
            </div>
            <div class="metric-cell">
              <div class="metric-label">{{ overBudget ? '预计超支' : '预计结余' }}</div>
              <div class="metric-value num" :class="overBudget ? 'is-over' : 'is-ok'">
                ¥{{ Math.abs((planBudget || 0) - grandTotal) }}
              </div>
            </div>
          </div>

          <div class="kv-grid">
            <div class="kv"><span class="kv-label">出发地</span><span class="kv-text">{{ plan.origin || '—' }}</span></div>
            <div class="kv"><span class="kv-label">目的地</span><span class="kv-text">{{ plan.destination || '—' }}</span></div>
            <div class="kv"><span class="kv-label">兴趣爱好</span><span class="kv-text">{{ plan.hobbies || '未填写' }}</span></div>
            <div class="kv"><span class="kv-label">其他偏好</span><span class="kv-text">{{ plan.preferences || '未填写' }}</span></div>
          </div>
        </template>
      </div>

      <!-- 质检明细：通过且无问题时不占独立卡片，避免「一行内容占一整卡」 -->
      <div v-if="itinerary.quality && (!itinerary.quality.passed || (itinerary.quality.issues && itinerary.quality.issues.length))"
        class="card no-print">
        <div class="row-center" style="margin-bottom: 10px">
          <el-tag :type="itinerary.quality.passed ? 'success' : 'warning'" effect="plain">
            {{ itinerary.quality.passed ? '行程标准校验通过' : '行程标准校验未通过' }}
          </el-tag>
          <span class="muted">
            达标率 {{ itinerary.quality.score }}% ｜ 未达标 {{ itinerary.quality.issueCount }} 项
            <template v-if="itinerary.quality.source"> ｜ 数据来源：{{ itinerary.quality.source }}</template>
          </span>
        </div>
        <ul v-if="itinerary.quality.issues && itinerary.quality.issues.length" class="qa-list">
          <li v-for="(iss, i) in itinerary.quality.issues" :key="i">
            <el-tag size="small" :type="iss.level === '阻断' ? 'danger' : 'info'">{{ iss.level }}</el-tag>
            <strong>{{ iss.rule }} · {{ iss.name }}</strong>
            <span class="muted">{{ iss.target }}</span>
            <span> — {{ iss.detail }}</span>
          </li>
        </ul>
      </div>

      <!-- 交通安排（往返 + 市内） -->
      <div v-if="itinerary.transport || editing" id="sec-transport" class="card">
        <div class="row-between" style="margin-bottom: 12px">
          <h3 class="section-title" style="margin: 0">交通安排</h3>
          <span class="hint-inline">{{ transportLegs.length }} 段 · 市内交通见下</span>
        </div>
        <template v-if="editing">
          <div class="kv-edit">
            <span class="kv-label">去程</span>
            <el-input v-model="itinerary.transport.outbound" type="textarea" :rows="2"
              placeholder="出发地 → 目的地：交通方式、站点、耗时、参考票价" />
          </div>
          <div class="kv-edit">
            <span class="kv-label">返程</span>
            <el-input v-model="itinerary.transport.inbound" type="textarea" :rows="2"
              placeholder="目的地 → 出发地：交通方式、站点、耗时、参考票价" />
          </div>
          <div class="kv-edit">
            <span class="kv-label">市内交通</span>
            <el-input v-model="itinerary.transport.local" type="textarea" :rows="2"
              placeholder="当地地铁/公交体系、推荐组合、票价区间" />
          </div>
          <div class="kv-edit">
            <span class="kv-label">可选方式</span>
            <div style="flex:1">
              <div v-for="(o, oi) in itinerary.transport.options" :key="oi"
                style="display:flex;gap:6px;margin-bottom:6px">
                <el-input v-model="itinerary.transport.options[oi]" placeholder="交通方式 + 耗时/票价/优缺点" />
                <el-button link type="danger" :icon="Delete"
                  @click="itinerary.transport.options.splice(oi, 1)" />
              </div>
              <el-button size="small" :icon="Plus" @click="itinerary.transport.options.push('')">添加方式</el-button>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="leg-list">
            <div v-for="leg in transportLegs" :key="leg.key" class="leg">
              <div class="leg-time">
                <span v-if="leg.start" class="num">{{ leg.start }}</span>
                <span v-if="leg.end" class="num leg-end">{{ leg.end }}</span>
              </div>
              <div class="leg-rail">
                <span class="leg-dot"></span>
                <span class="leg-line"></span>
                <span class="leg-dot hollow"></span>
              </div>
              <div class="leg-body">
                <div class="leg-head">
                  <span v-if="leg.code" class="leg-code">{{ leg.code }}</span>
                  <span class="leg-name">{{ leg.label }}</span>
                </div>
                <div class="leg-desc">{{ leg.desc || '—' }}</div>
              </div>
              <div v-if="leg.price" class="leg-price">
                <span class="num">¥{{ leg.price }}</span>
                <span v-if="leg.seat" class="leg-seat">{{ leg.seat }}</span>
              </div>
            </div>
          </div>

          <div v-if="itinerary.transport.local" class="subtle-box local-transport">
            <span class="local-tag">市内交通</span>
            <span>{{ itinerary.transport.local }}</span>
          </div>

          <div v-if="itinerary.transport.options && itinerary.transport.options.length" class="options-block">
            <div class="metric-label" style="margin-bottom: 6px">其他可选方式</div>
            <ul class="plain-list">
              <li v-for="(o, oi) in itinerary.transport.options" :key="oi">{{ o }}</li>
            </ul>
          </div>
        </template>
      </div>

      <!-- 住宿推荐 + 住宿预算与酒店选择（紧随交通安排之后） -->
      <div v-if="hotels.length || editing" id="sec-stay" class="card">
        <div class="stay-head">
          <h3 class="section-title" style="margin: 0">住宿推荐与预算</h3>
          <span class="muted">
            共 {{ nights }} 晚
            <template v-if="selectedHotel"> ｜ 当前住宿：{{ selectedHotel.name }}</template>
          </span>
        </div>

        <p v-if="stay.customLocation" class="muted" style="margin:0 0 8px;font-size:12px">
          你已在地图上指定住宿位置「<b>{{ stay.customLocation.name }}</b>」，
          下方「行程地图」的每日动线均以此为出发点与返回点。
          <el-button link type="primary" size="small" @click="onClearPickedHotel">恢复推荐酒店位置</el-button>
        </p>

        <!-- 不选用推荐酒店时，住宿花销由用户自己填，保证总花销算得对 -->
        <div v-if="stay.customLocation" class="custom-cost-row">
          <span class="stay-ctrl-label">住宿花销</span>
          <template v-if="customCostEditing || customNightly > 0">
            <el-input-number v-model="customPricePerNight" :min="0" :max="budgetMax" :step="50"
              controls-position="right" style="width: 150px" @change="persistStayQuietly" />
            <span class="stay-calc">元 / 晚 × {{ nights }} 晚 = <b>¥{{ customNightly * nights }}</b></span>
            <el-button size="small" link type="danger" @click="clearCustomPrice">清除</el-button>
          </template>
          <el-button v-else size="small" type="primary" plain :icon="Plus" @click="customCostEditing = true">
            自主添加住宿花销
          </el-button>
          <span class="muted">
            {{ customNightly > 0 ? '总花销已按此金额计入' : '不填则按当前推荐酒店单价计算' }}
          </span>
        </div>

        <!-- 住宿预算调节：预算变化 → 推荐酒店实时重匹配 -->
        <div class="stay-panel">
          <div class="stay-ctrl">
            <span class="stay-ctrl-label">每晚预算</span>
            <el-slider v-model="stayBudgetPerNight" :min="0" :max="budgetMax" :step="10"
              style="flex:1;min-width:150px" @change="onBudgetChange" />
            <el-input-number v-model="stayBudgetPerNight" :min="0" :max="budgetMax" :step="50"
              size="small" controls-position="right" style="width:132px" @change="onBudgetChange" />
            <span class="stay-calc">× {{ nights }} 晚 = <b>¥{{ stayBudget }}</b></span>
            <el-button v-if="selectedHotel" size="small" link type="primary"
              @click="matchHotelPrice">按当前酒店定价</el-button>
          </div>

          <div class="stay-stats">
            <div class="stat">
              <span>住宿预算</span>
              <b>¥{{ stayBudget }}</b>
            </div>
            <div class="stat">
              <span>当前住宿花费</span>
              <b class="hl">¥{{ stayCost }}</b>
            </div>
            <div class="stat" :class="surplus >= 0 ? 'ok' : 'over'">
              <span>{{ surplus >= 0 ? '住宿结余' : '超出住宿预算' }}</span>
              <b>¥{{ Math.abs(surplus) }}</b>
            </div>
            <div class="stat">
              <span>预算内可选酒店</span>
              <b class="match">{{ withinBudgetCount }} / {{ hotels.length }} 家</b>
            </div>
          </div>

          <!-- 推荐酒店随预算浮动：与当前所选不一致时给出提示 -->
          <el-alert v-if="recommendedHotel && !isSelected(recommendedHotel)" type="info" :closable="false" show-icon
            class="stay-alert">
            <template #title>
              按当前每晚预算 ¥{{ stayBudgetPerNight }}，更匹配的是「{{ recommendedHotel.name }}」
              （¥{{ hotelPrice(recommendedHotel) }}/晚）
            </template>
            <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
              <span>预算内共 {{ withinBudgetCount }} 家可选，该酒店是其中价位最贴合的一家。</span>
              <el-button size="small" type="primary" plain @click="chooseHotel(recommendedHotel)">采用推荐</el-button>
            </div>
          </el-alert>

          <el-alert v-if="surplus > 0" type="success" :closable="false" show-icon class="stay-alert">
            <template #title>住宿预算尚余 ¥{{ surplus }}</template>
            未用完的住宿预算不再预划购物额度，是否购物、花多少完全由你自行决定（下方「购物推荐」只列去处）。
          </el-alert>
          <el-alert v-else-if="surplus < 0" type="warning" :closable="false" show-icon class="stay-alert">
            <template #title>住宿超出预算 ¥{{ -surplus }}</template>
            可切换更经济的酒店，或把每晚预算上调到 ¥{{ selectedHotel ? hotelPrice(selectedHotel) : budgetMax }}。
          </el-alert>
          <el-alert v-else type="info" :closable="false" show-icon class="stay-alert">
            <template #title>住宿预算刚好用尽</template>
            当前酒店单价与每晚预算持平，无结余。
          </el-alert>

          <p class="stay-hint muted">
            调整每晚预算后，系统会按新预算重新匹配推荐酒店（预算内单价最贴合的一家），住宿花费、预算合计随之重算；
            地图里的每日动线也会同步改为「从所选酒店出发、当天结束返回酒店」。
          </p>
        </div>

        <!-- 酒店列表 -->
        <div v-for="(h, hi) in hotels" :key="hi" class="hotel-item" :class="{ 'is-selected': isSelected(h) }">
          <template v-if="editing">
            <el-row :gutter="8">
              <el-col :span="7"><el-input v-model="h.name" placeholder="酒店名称" /></el-col>
              <el-col :span="6"><el-input v-model="h.area" placeholder="所在区域" /></el-col>
              <el-col :span="5"><el-input v-model="h.price" placeholder="大致价位，如 350-500 元/晚" /></el-col>
              <el-col :span="3"><el-input v-model="h.level" placeholder="档次" /></el-col>
              <el-col :span="3">
                <el-select v-model="h.category" placeholder="分类">
                  <el-option label="推荐" value="推荐" />
                  <el-option label="备选" value="备选" />
                </el-select>
              </el-col>
            </el-row>
            <el-row :gutter="8" style="margin-top:6px">
              <el-col :span="12"><el-input v-model="h.reason" placeholder="推荐理由" /></el-col>
              <el-col :span="11"><el-input v-model="h.advantage" placeholder="位置优势" /></el-col>
              <el-col :span="1">
                <el-button link type="danger" :icon="Delete" @click="itinerary.hotels.splice(hi, 1)" />
              </el-col>
            </el-row>
            <div style="margin-top:6px;display:flex;align-items:center;gap:10px;flex-wrap:wrap">
              <el-button size="small" :type="isSelected(h) ? 'success' : 'primary'" :plain="!isSelected(h)"
                @click="chooseHotel(h)">
                {{ isSelected(h) ? '当前住宿' : '选为我的住宿' }}
              </el-button>
              <span class="muted">解析单价 ¥{{ hotelPrice(h) }}/晚 × {{ nights }} 晚 = ¥{{ hotelPrice(h) * nights }}</span>
            </div>
          </template>
          <template v-else>
            <div class="hotel-head">
              <el-tag size="small" :type="isRecommend(h) ? 'success' : 'info'" effect="dark">
                {{ h.category || '备选' }}
              </el-tag>
              <strong>{{ h.name }}</strong>
              <el-tag v-if="h.level" size="small" effect="plain">{{ h.level }}</el-tag>
              <span class="hotel-price">{{ h.price }}</span>
              <el-tag v-if="isRecommended(h)" size="small" type="success" effect="dark">预算内推荐</el-tag>
              <el-tag v-else-if="isOverBudget(h)" size="small" type="danger" effect="plain">超出预算</el-tag>
              <el-tag v-else-if="isWithinBudget(h)" size="small" type="info" effect="plain">预算内</el-tag>
              <el-tag v-if="isSelected(h)" size="small" type="warning" effect="dark">当前住宿</el-tag>
              <span class="stay-cost">{{ nights }} 晚共计 ¥{{ hotelPrice(h) * nights }}</span>
              <span style="flex:1"></span>
              <el-button size="small" :type="isSelected(h) ? 'success' : 'primary'" :plain="!isSelected(h)"
                @click="chooseHotel(h)">
                {{ isSelected(h) ? '已选择' : '选为我的住宿' }}
              </el-button>
            </div>
            <div class="hotel-line"><span class="kv-label">所在区域</span>{{ h.area || '—' }}</div>
            <div class="hotel-line"><span class="kv-label">推荐理由</span>{{ h.reason || '—' }}</div>
            <div class="hotel-line"><span class="kv-label">位置优势</span>{{ h.advantage || '—' }}</div>
          </template>
        </div>
        <el-button v-if="editing" size="small" :icon="Plus" @click="addHotel">添加酒店</el-button>
      </div>

      <!-- 购物推荐（只推荐去处，不设任何额度） -->
      <div v-if="(itinerary.shopping && itinerary.shopping.length) || editing" class="card">
        <div class="stay-head">
          <h3 class="section-title" style="margin: 0">购物推荐</h3>
          <span class="muted">只推荐去处，不设消费额度 —— 买与不买、花多少由你自行决定</span>
        </div>
        <p class="muted" style="margin:0 0 12px;font-size:12px;line-height:1.7">
          以下仅为值得一逛的街区、商圈与市场，供你自由取舍，不占用也不计入行程预算。
        </p>

        <div v-for="(s, si) in itinerary.shopping || []" :key="si" class="shop-item">
          <template v-if="editing">
            <el-row :gutter="8">
              <el-col :span="8"><el-input v-model="s.name" placeholder="地点 / 区域名称" /></el-col>
              <el-col :span="8"><el-input v-model="s.area" placeholder="所在区域 / 商圈" /></el-col>
              <el-col :span="7"><el-input v-model="s.type" placeholder="类型，如 特产伴手礼" /></el-col>
              <el-col :span="1">
                <el-button link type="danger" :icon="Delete" @click="itinerary.shopping.splice(si, 1)" />
              </el-col>
            </el-row>
            <el-row :gutter="8" style="margin-top:6px">
              <el-col :span="12"><el-input v-model="s.buyWhat" placeholder="适合买什么（具体品类）" /></el-col>
              <el-col :span="12"><el-input v-model="s.suitableFor" placeholder="适合人群 / 场景" /></el-col>
            </el-row>
            <el-row :gutter="8" style="margin-top:6px">
              <el-col :span="23"><el-input v-model="s.tips" placeholder="实用提示：营业时间、砍价、能否快递" /></el-col>
            </el-row>
          </template>
          <template v-else>
            <div class="shop-head">
              <el-tag size="small" effect="dark" type="warning">{{ s.type || '购物' }}</el-tag>
              <strong>{{ s.name }}</strong>
              <span v-if="s.area" class="muted">{{ s.area }}</span>
            </div>
            <div class="hotel-line"><span class="kv-label">适合买</span>{{ s.buyWhat || '—' }}</div>
            <div class="hotel-line"><span class="kv-label">适合人群</span>{{ s.suitableFor || '—' }}</div>
            <div class="hotel-line"><span class="kv-label">实用提示</span>{{ s.tips || '—' }}</div>
          </template>
        </div>
        <el-button v-if="editing" size="small" :icon="Plus" @click="addShopping">添加购物推荐</el-button>
      </div>

      <!-- 行程地图 -->
      <div id="sec-map">
      <div v-if="stayBase" class="stay-base no-print">
        <el-icon><House /></el-icon>
        <span>
          每日动线以「<b>{{ stayBase.name }}</b>」为出发点与返回点
          <template v-if="stayBase.note">（{{ stayBase.note }}）</template>，
          地图已按当前住宿重排。
          <el-tag v-if="stay.customLocation" size="small" type="warning" effect="plain" style="margin-left:6px">
            地图自选位置
          </el-tag>
          <span v-if="customNightly > 0" class="muted">
            ｜ 住宿花销按自填的 ¥{{ customNightly }}/晚核算
          </span>
          <span v-else-if="customMismatch" class="muted">
            ｜ 预算按「{{ selectedHotel.name }}」核算，未填自选花销
          </span>
        </span>
      </div>
      <itinerary-map v-if="plan" :plan="plan" class="no-print"
        @pick-hotel="onPickHotel" @clear-hotel="onClearPickedHotel" />
      </div>

      <!-- 每日行程 -->
      <div v-if="itinerary.days && itinerary.days.length" id="sec-days">
        <div v-for="(day, di) in itinerary.days" :key="di" class="card">
          <div style="display:flex;align-items:center;gap:10px;margin-bottom:14px">
            <el-tag type="primary" effect="dark">第 {{ day.day }} 天</el-tag>
            <template v-if="editing">
              <el-input v-model="day.theme" placeholder="当日主题" style="max-width:240px" />
              <el-input v-model="day.date" placeholder="日期描述" style="max-width:180px" />
              <el-button link type="danger" :icon="Delete" @click="removeDay(di)">删除本天</el-button>
            </template>
            <template v-else>
              <strong>{{ day.theme }}</strong>
              <span class="muted">{{ day.date }}</span>
            </template>
          </div>

          <!-- 当天交通小结 -->
          <div v-if="editing" style="margin-bottom:12px">
            <el-input v-model="day.transport" placeholder="当天交通小结：景点之间怎么走" />
          </div>
          <div v-else-if="day.transport" class="day-transport">
            <span class="day-transport-label">当天交通</span>{{ day.transport }}
          </div>

          <div v-for="(item, ii) in day.items || []" :key="ii" class="timeline-item">
            <template v-if="editing">
              <el-row :gutter="8">
                <el-col :span="4"><el-input v-model="item.time" placeholder="时间" /></el-col>
                <el-col :span="5"><el-input v-model="item.place" placeholder="地点" /></el-col>
                <el-col :span="7"><el-input v-model="item.activity" placeholder="安排" /></el-col>
                <el-col :span="3"><el-input v-model="item.cost" placeholder="花费" /></el-col>
                <el-col :span="4"><el-input v-model="item.tips" placeholder="贴士" /></el-col>
                <el-col :span="1">
                  <el-button link type="danger" :icon="Delete" @click="removeItem(day, ii)" />
                </el-col>
              </el-row>
              <el-row :gutter="8" style="margin-top:6px">
                <el-col :span="23">
                  <el-input v-model="item.transport" placeholder="从上一地点到此处的交通方式与耗时" />
                </el-col>
              </el-row>
            </template>
            <template v-else>
              <div class="item-row">
                <span class="item-time num">{{ item.time || '—' }}</span>
                <div class="item-main">
                  <div class="item-head">
                    <strong class="item-place">{{ item.place }}</strong>
                    <span v-if="item.cost" class="cost-tag">{{ item.cost }}</span>
                    <span style="flex: 1"></span>
                    <el-button link type="warning" :icon="Star" size="small" @click="favorite(item)">收藏</el-button>
                  </div>
                  <div class="item-activity">{{ item.activity }}</div>
                  <div v-if="item.transport" class="item-transport">↳ {{ item.transport }}</div>
                  <div v-if="item.tips" class="item-tips">{{ item.tips }}</div>
                </div>
              </div>
            </template>
          </div>

          <el-button v-if="editing" size="small" :icon="Plus" @click="addItem(day)">添加事项</el-button>
          <el-empty v-else-if="!day.items || !day.items.length" description="当天暂无安排" :image-size="60" />
        </div>

        <div v-if="editing" class="card">
          <el-button type="primary" plain :icon="Plus" @click="addDay">添加一天</el-button>
        </div>
      </div>

      <!-- 预算分配 -->
      <div v-if="itinerary.budget || editing" id="sec-budget" class="card">
        <div class="stay-head">
          <h3 class="section-title" style="margin: 0">预算分配</h3>
          <span class="muted">住宿金额随所选酒店实时更新；购物不设额度、不计入合计</span>
        </div>
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="交通">{{ budget.transport || '—' }}</el-descriptions-item>
          <el-descriptions-item label="住宿">
            ¥{{ stayCost }} 元
            <span v-if="stayCostLabel" class="muted">（{{ stayCostLabel }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="餐饮">{{ budget.food || '—' }}</el-descriptions-item>
          <el-descriptions-item label="门票">{{ budget.tickets || '—' }}</el-descriptions-item>
          <el-descriptions-item label="其他">{{ budget.other || '—' }}</el-descriptions-item>
          <el-descriptions-item label="合计" :span="3">
            <b>¥{{ grandTotal }}</b> 元
          </el-descriptions-item>
        </el-descriptions>

        <div class="total-row">
          <span>总预算：<b>¥{{ planBudget || '未设置' }}</b></span>
          <el-tag v-if="planBudget && !overBudget" type="success" size="small">
            预算内，尚余 ¥{{ planBudget - grandTotal }}
          </el-tag>
          <el-tag v-else-if="overBudget" type="danger" size="small">
            超出总预算 ¥{{ grandTotal - planBudget }}
          </el-tag>
          <span class="muted">购物消费由你自行决定，不预先占用预算</span>
        </div>
      </div>

      <!-- 出行提示 -->
      <div v-if="itinerary.tips && itinerary.tips.length" class="card">
        <h3 class="section-title">出行提示</h3>
        <ol style="margin:0;padding-left:20px;line-height:1.9">
          <li v-for="(t, i) in itinerary.tips" :key="i">{{ t }}</li>
        </ol>
      </div>
    </template>

    <el-empty v-else description="方案不存在或已删除" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Check, Refresh, Download, Printer, Delete, Star, Plus, House, ArrowLeft, MoreFilled } from '@element-plus/icons-vue'
import { fetchPlan, updatePlan, deletePlan, regeneratePlan } from '@/api/plan'
import { addFavorite } from '@/api/favorite'
import { itineraryToMarkdown, downloadText } from '@/utils/export'
import ItineraryMap from '@/components/ItineraryMap.vue'
import AnchorNav from '@/components/AnchorNav.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const regenLoading = ref(false)
const editing = ref(false)
const plan = ref(null)
const snapshot = ref('')
/** 用户是否手动指定过酒店（手动后不再随预算自动改写所选） */
const manualPick = ref(false)

/** 页内锚点导航（与卡片 id 一一对应，顺序不可随意调整） */
const anchors = [
  { id: 'sec-overview', label: '概览' },
  { id: 'sec-transport', label: '交通' },
  { id: 'sec-stay', label: '住宿' },
  { id: 'sec-map', label: '地图' },
  { id: 'sec-days', label: '每日行程' },
  { id: 'sec-budget', label: '预算' }
]

/** 操作栏「更多」下拉：破坏性操作与常规操作分离 */
function onBarCommand(cmd) {
  if (cmd === 'delete') remove()
}

const editForm = reactive({ title: '', summary: '' })

const itinerary = computed(() => (plan.value && plan.value.itinerary) || {})
const hotels = computed(() => itinerary.value.hotels || [])
const budget = computed(() => itinerary.value.budget || {})

/**
 * 交通：把「去程：…（07:30-10:05，约 2 小时 35 分）至长沙南，二等座约 314 元；…」
 * 拆成 时刻 / 车次 / 描述 / 票价 四列，便于横向比对。
 * 拆不出来时（数据不规范）仍原样展示，不影响阅读。
 */
function parseLeg(label, text) {
  const raw = String(text || '').replace(/^\s*(去程|返程)\s*[：:]\s*/, '').trim()
  const times = raw.match(/(\d{1,2}:\d{2})\s*[-–~至]\s*(\d{1,2}:\d{2})/)
  const code = raw.match(/\b([GDCZTKY]\d{1,4})\b/)
  const price = raw.match(/(\d{2,5})\s*元/)
  const seat = raw.match(/(二等座|一等座|商务座|硬卧|软卧|硬座|经济舱|特价机票)/)
  let desc = raw
    .replace(/（[^）]{0,40}\d{1,2}:\d{2}\s*[-–~至]\s*\d{1,2}:\d{2}[^）]{0,40}）/g, '')
    .replace(/[，,]\s*(?:二等座|一等座|商务座|硬卧|软卧|硬座|经济舱|特价机票)?\s*约?\s*\d{2,5}\s*元/g, '')
  // 车次已在左侧胶囊单独展示，正文里去掉避免重复
  if (code) desc = desc.replace(new RegExp('\\s*' + code[1] + '\\s*次?'), '')
  desc = desc.replace(/\s{2,}/g, ' ').replace(/乘高铁\s*至/, '乘高铁至').trim()
  return {
    key: label,
    label,
    start: times ? times[1] : '',
    end: times ? times[2] : '',
    code: code ? code[1] : '',
    price: price ? price[1] : '',
    seat: seat ? seat[1] : '',
    desc
  }
}

const transportLegs = computed(() => {
  const t = itinerary.value.transport || {}
  return [parseLeg('去程', t.outbound), parseLeg('返程', t.inbound)].filter((l) => l.desc)
})
const planBudget = computed(() => Number(plan.value && plan.value.budget) || 0)

/* ---------------------------------- 住宿预算 ---------------------------------- */

/** 从「420-560 元/晚」这类文本解析每晚单价（取区间中值），解析不出时回退到后端字段 */
function hotelPrice(hotel) {
  if (!hotel) return 0
  const nums = String(hotel.price || '').match(/\d+(?:\.\d+)?/g)
  if (nums && nums.length) {
    const arr = nums.slice(0, 4).map(Number)
    const v = arr.length === 1 ? arr[0] : (Math.min(...arr) + Math.max(...arr)) / 2
    if (v > 0) return Math.round(v)
  }
  return Number(hotel.pricePerNight) > 0 ? Math.round(Number(hotel.pricePerNight)) : 0
}

/** 从「约 800 元（…）」取第一个数字 */
function money(text) {
  const m = String(text == null ? '' : text).match(/\d+(?:\.\d+)?/g)
  return m && m.length ? Number(m[0]) : 0
}

/** 保证 stay / shopping 结构存在并填补缺失默认值（旧方案数据兜底） */
function ensureStay() {
  const it = plan.value && plan.value.itinerary
  if (!it) return {}
  if (!it.hotels) it.hotels = []
  if (!it.shopping) it.shopping = []
  if (!it.stay) it.stay = {}
  const s = it.stay
  const list = it.hotels
  if (!(s.nights > 0)) {
    const d = Number(plan.value.days) || (it.days || []).length || 1
    s.nights = Math.max(1, d - 1)
  }
  if (!s.selectedHotel || !list.some((h) => h.name === s.selectedHotel)) {
    const rec = list.find((h) => String(h.category || '').includes('推荐')) || list[0]
    if (rec) s.selectedHotel = rec.name
  }
  if (!(s.budgetPerNight > 0)) {
    const sel = list.find((h) => h.name === s.selectedHotel)
    if (sel) s.budgetPerNight = hotelPrice(sel)
  }
  return s
}

const stay = computed(() => itinerary.value.stay || {})

const nights = computed(() => {
  const n = Number(stay.value.nights)
  if (n > 0) return n
  const d = Number(plan.value && plan.value.days) || (itinerary.value.days || []).length || 1
  return Math.max(1, d - 1)
})

const selectedHotel = computed(
  () => hotels.value.find((h) => h.name === stay.value.selectedHotel) || null
)

/** 动线基点：优先用户在地图上指定的位置，其次才是推荐酒店 */
const stayBase = computed(() => {
  const c = stay.value.customLocation
  if (c && c.name) return { name: c.name, note: c.address || '' }
  if (selectedHotel.value) {
    return { name: selectedHotel.value.name, note: selectedHotel.value.area || '' }
  }
  return null
})

/** 地图自选位置与推荐列表里的酒店不是同一家（此时预算仍按选中酒店核算） */
const customMismatch = computed(() => {
  const c = stay.value.customLocation
  return !!(c && c.name && selectedHotel.value && c.name !== selectedHotel.value.name)
})

/* ------------------------ 自选住宿的花销（用户自填） ------------------------ */

/** 用户自填的每晚住宿花销；0 表示没填 */
const customNightly = computed(() => Number(stay.value.customPricePerNight) || 0)
/** 是否已展开「自主添加住宿花销」的输入框 */
const customCostEditing = ref(false)
/** 实际用于计算的每晚住宿花销：优先用户自填，其次推荐酒店单价 */
const effectiveNightly = computed(() => {
  if (customNightly.value > 0) return customNightly.value
  return selectedHotel.value ? hotelPrice(selectedHotel.value) : 0
})

/** 输入框双向绑定：填 0 视为清除，避免把 0 存进方案里 */
const customPricePerNight = computed({
  get() {
    return customNightly.value
  },
  set(v) {
    const s = ensureStay()
    const n = Math.max(0, Math.round(Number(v) || 0))
    if (n > 0) s.customPricePerNight = n
    else delete s.customPricePerNight
  }
})

function clearCustomPrice() {
  const s = ensureStay()
  delete s.customPricePerNight
  customCostEditing.value = false
  ElMessage.success('已清除自选住宿花销，改按推荐酒店单价计算')
  persistStayQuietly()
}

/** 每晚住宿预算（可调，双向绑定回 itinerary.stay） */
const stayBudgetPerNight = computed({
  get() {
    const v = Number(stay.value.budgetPerNight)
    if (v > 0) return v
    return effectiveNightly.value
  },
  set(v) {
    const s = ensureStay()
    s.budgetPerNight = Math.max(0, Number(v) || 0)
    s.nights = nights.value
  }
})

/** 滑块上限：最高房价的 1.5 倍，取整到百 */
const budgetMax = computed(() => {
  const prices = hotels.value.map(hotelPrice).filter((p) => p > 0)
  const top = prices.length ? Math.max(...prices) : 800
  return Math.max(500, Math.ceil((top * 1.5) / 100) * 100)
})

const stayBudget = computed(() => stayBudgetPerNight.value * nights.value)
/** 住宿实际花销：自选填了花销就按自填，否则按推荐酒店单价 */
const stayCost = computed(() => effectiveNightly.value * nights.value)
/** 住宿花销的口径说明，自选住宿时显示自选地点名 */
const stayCostLabel = computed(() => {
  if (customNightly.value > 0) {
    const c = stay.value.customLocation
    return `${(c && c.name) || '自选住宿'} × ${nights.value} 晚`
  }
  return selectedHotel.value ? `${selectedHotel.value.name} × ${nights.value} 晚` : ''
})
const surplus = computed(() => stayBudget.value - stayCost.value)

/**
 * 按当前每晚预算匹配的推荐酒店。
 * 规则：预算内单价最高的一家（把预算用足、体验最好）；若全部超出则取最便宜的一家兜底。
 * 预算一变，这里的结果立刻跟着变 —— 这就是「推荐随预算区间浮动而更新」。
 */
const recommendedHotel = computed(() => {
  const list = hotels.value
  if (!list.length) return null
  const limit = Number(stayBudgetPerNight.value) || 0
  let best = null
  let cheapest = null
  list.forEach((h) => {
    const p = hotelPrice(h)
    if (!(p > 0)) return
    if (!cheapest || p < hotelPrice(cheapest)) cheapest = h
    if (limit > 0 && p <= limit && (!best || p > hotelPrice(best))) best = h
  })
  return best || cheapest || list[0]
})

/** 当前预算内可选酒店数量 */
const withinBudgetCount = computed(() => {
  const limit = Number(stayBudgetPerNight.value) || 0
  if (!(limit > 0)) return hotels.value.length
  return hotels.value.filter((h) => hotelPrice(h) > 0 && hotelPrice(h) <= limit).length
})

/** 除住宿外的固定项之和 */
const otherCost = computed(
  () =>
    money(budget.value.transport) +
    money(budget.value.food) +
    money(budget.value.tickets) +
    money(budget.value.other)
)
const grandTotal = computed(() => otherCost.value + stayCost.value)
const overBudget = computed(() => planBudget.value > 0 && grandTotal.value > planBudget.value)

function isSelected(hotel) {
  return !!hotel && hotel.name === stay.value.selectedHotel
}
function isRecommended(hotel) {
  return !!hotel && !!recommendedHotel.value && hotel.name === recommendedHotel.value.name
}
function isOverBudget(hotel) {
  const limit = Number(stayBudgetPerNight.value) || 0
  return limit > 0 && hotelPrice(hotel) > limit
}
function isWithinBudget(hotel) {
  const limit = Number(stayBudgetPerNight.value) || 0
  return limit > 0 && hotelPrice(hotel) > 0 && hotelPrice(hotel) <= limit
}

/** 每晚预算变化：自动把所选酒店对齐到新的推荐（用户手动指定过则不再自动改写） */
function onBudgetChange() {
  if (manualPick.value) return
  const rec = recommendedHotel.value
  if (rec && rec.name !== stay.value.selectedHotel) {
    const s = ensureStay()
    s.selectedHotel = rec.name
    s.nights = nights.value
  }
}

function chooseHotel(hotel) {
  if (!hotel) return
  const s = ensureStay()
  // 选用推荐酒店 → 不再使用地图自选位置与自填花销，动线回到该酒店
  const hadCustom = !!(s.customLocation || s.customPricePerNight)
  delete s.customLocation
  delete s.customPricePerNight
  customCostEditing.value = false
  s.selectedHotel = hotel.name
  s.nights = nights.value
  // 选的就是口径内的推荐 → 继续跟随预算；选了别的 → 视为手动指定
  manualPick.value = !isRecommended(hotel)
  ElMessage.success({
    message: `已切换为「${hotel.name}」，住宿与预算合计已重算${hadCustom ? '（已取消地图自选位置）' : ''}`,
    duration: 2200
  })
}

/** 把每晚预算对齐到当前酒店单价（超支时的一键处理） */
function matchHotelPrice() {
  if (!selectedHotel.value) return
  manualPick.value = true
  stayBudgetPerNight.value = hotelPrice(selectedHotel.value)
}

/* --------------------------- 在地图上指定住宿位置 --------------------------- */

/** 地图上搜索选中 / 点选确认了一个住宿位置 */
function onPickHotel(p) {
  if (!p) return
  const lat = Number(p.lat)
  const lng = Number(p.lng)
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return

  const s = ensureStay()
  s.customLocation = {
    name: p.name || '地图选点',
    address: p.address || '',
    lat,
    lng,
    source: p.source || 'map'
  }
  // 若与推荐列表里的某家同名，同步选中它，让住宿预算跟着走
  const matched = hotels.value.find((h) => h.name === p.name)
  if (matched) {
    s.selectedHotel = matched.name
    s.nights = nights.value
    manualPick.value = !isRecommended(matched)
  }
  ElMessage.success({
    message: `住宿位置已改为「${s.customLocation.name}」，每日路线已按新位置重排`,
    duration: 2200
  })
  persistStayQuietly()
}

/** 恢复为推荐酒店的住宿位置 */
function onClearPickedHotel() {
  const s = ensureStay()
  if (!s.customLocation) return
  delete s.customLocation
  ElMessage.success('已恢复为推荐酒店的住宿位置，路线已重排')
  persistStayQuietly()
}

/**
 * 住宿改动是否立即落库：
 * 编辑模式下不自动保存（等用户点「保存修改」），非编辑模式下直接持久化，
 * 避免用户以为改好了、刷新后却丢了。
 */
async function persistStayQuietly() {
  if (editing.value || !plan.value) return
  try {
    applyBudget()
    await updatePlan(plan.value.id, {
      title: plan.value.title,
      summary: plan.value.summary,
      itinerary: plan.value.itinerary
    })
    const c = stay.value.customLocation
    ElMessage({ message: c ? '住宿位置已保存' : '住宿位置已恢复并保存', type: 'success', duration: 1600 })
  } catch (e) {
    // request.js 已经弹出可操作的中文提示，这里不重复提示
  }
}

/** 把实时核算结果写回 budget，供保存与导出使用 */
function applyBudget() {
  const it = plan.value && plan.value.itinerary
  if (!it) return
  // 同步每家的解析单价，保证后端/导出与界面一致
  ;(it.hotels || []).forEach((h) => {
    const p = hotelPrice(h)
    if (p > 0) h.pricePerNight = p
  })
  if (!it.budget) it.budget = {}
  const sel = selectedHotel.value
  if (customNightly.value > 0) {
    // 自选住宿：按用户自己填的花销计入
    const cname = (stay.value.customLocation && stay.value.customLocation.name) || '自选住宿'
    it.budget.accommodation = `约 ${stayCost.value} 元（${cname} ${customNightly.value} 元/晚 × ${nights.value} 晚）`
  } else {
    it.budget.accommodation = sel
      ? `约 ${stayCost.value} 元（${sel.name} ${hotelPrice(sel)} 元/晚 × ${nights.value} 晚）`
      : `约 ${stayCost.value} 元`
  }
  // 购物不设额度：预算里不再写购物金额
  delete it.budget.shopping
  it.budget.total = `约 ${grandTotal.value} 元`
}

/* ----------------------------------- 加载 ----------------------------------- */

async function load() {
  loading.value = true
  try {
    const res = await fetchPlan(route.params.id)
    plan.value = res.data
    ensureStay()
    manualPick.value = false
  } finally {
    loading.value = false
  }
}

function startEdit() {
  if (!plan.value.itinerary) {
    plan.value.itinerary = { title: plan.value.title, summary: plan.value.summary, days: [], tips: [] }
  }
  const it = plan.value.itinerary
  if (!it.days) {
    it.days = []
  }
  if (!it.hotels) {
    it.hotels = []
  }
  if (!it.shopping) {
    it.shopping = []
  }
  if (!it.transport) {
    it.transport = { outbound: '', inbound: '', local: '', options: [] }
  }
  if (!it.transport.options) {
    it.transport.options = []
  }
  if (!it.budget) {
    it.budget = { transport: '', accommodation: '', food: '', tickets: '', other: '', total: '' }
  }
  ensureStay()
  snapshot.value = JSON.stringify(plan.value)
  editForm.title = plan.value.title
  editForm.summary = plan.value.summary || ''
  editing.value = true
}

function cancelEdit() {
  plan.value = JSON.parse(snapshot.value)
  ensureStay()
  manualPick.value = false
  editing.value = false
}

async function save() {
  saving.value = true
  try {
    applyBudget()
    await updatePlan(plan.value.id, {
      title: editForm.title,
      summary: editForm.summary,
      itinerary: plan.value.itinerary
    })
    plan.value.title = editForm.title
    plan.value.summary = editForm.summary
    editing.value = false
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

async function regenerate() {
  await ElMessageBox.confirm('将根据原条件重新调用大模型生成，覆盖当前行程内容，确定继续？', '重新生成', { type: 'warning' })
  regenLoading.value = true
  try {
    await regeneratePlan(plan.value.id)
    ElMessage.success('已重新生成')
    await load()
  } finally {
    regenLoading.value = false
  }
}

function addDay() {
  const days = plan.value.itinerary.days || (plan.value.itinerary.days = [])
  days.push({ day: days.length + 1, theme: '新的一天', date: '', transport: '', items: [] })
}

function removeDay(index) {
  plan.value.itinerary.days.splice(index, 1)
  plan.value.itinerary.days.forEach((d, i) => (d.day = i + 1))
}

function addItem(day) {
  if (!day.items) day.items = []
  day.items.push({ time: '', place: '', activity: '', transport: '', cost: '', tips: '' })
}

function removeItem(day, index) {
  day.items.splice(index, 1)
}

function addHotel() {
  if (!plan.value.itinerary.hotels) plan.value.itinerary.hotels = []
  plan.value.itinerary.hotels.push({
    name: '', area: '', price: '', reason: '', advantage: '', level: '舒适型', category: '备选'
  })
}

function addShopping() {
  if (!plan.value.itinerary.shopping) plan.value.itinerary.shopping = []
  plan.value.itinerary.shopping.push({
    name: '', area: '', type: '特产伴手礼', buyWhat: '', suitableFor: '', tips: ''
  })
}

function isRecommend(hotel) {
  return !!(hotel.category && hotel.category.includes('推荐'))
}

async function favorite(item) {
  try {
    await addFavorite({
      planId: plan.value.id,
      name: item.place || item.activity || '未命名地点',
      city: plan.value.destination,
      category: '景点',
      description: item.activity,
      rating: '',
      address: ''
    })
    ElMessage.success(`已收藏「${item.place}」`)
  } catch (e) {
    /* 已收藏等错误由拦截器统一提示 */
  }
}

function exportMd() {
  applyBudget()
  downloadText(`${plan.value.title || '旅行方案'}.md`, itineraryToMarkdown(plan.value))
}

function printPage() {
  window.print()
}

function remove() {
  ElMessageBox.confirm('删除后不可恢复，确定删除该方案？', '删除确认', { type: 'warning' })
    .then(async () => {
      await deletePlan(plan.value.id)
      ElMessage.success('已删除')
      router.push('/plans')
    })
    .catch(() => {})
}

onMounted(load)
</script>

<style scoped>
.qa-list {
  margin: 12px 0 0;
  padding-left: 18px;
  line-height: 1.9;
}
.qa-list li {
  margin-bottom: 4px;
}
.qa-list strong {
  margin: 0 6px;
}
.kv {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter, #ebeef5);
}
.kv:last-child {
  border-bottom: none;
}
.kv-edit {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 10px;
}
.kv-edit .kv-label {
  padding-top: 6px;
}
.kv-label {
  flex: 0 0 68px;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
}
.kv-text {
  flex: 1;
  line-height: 1.8;
  color: var(--el-text-color-primary, #303133);
}
.plain-list {
  margin: 0;
  padding-left: 18px;
}
.day-transport {
  margin: -4px 0 14px;
  padding: 8px 12px;
  border-left: 3px solid var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
  border-radius: 4px;
  line-height: 1.7;
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
}
.day-transport-label {
  display: inline-block;
  margin-right: 8px;
  color: var(--el-color-primary, #409eff);
  font-weight: 600;
}
.item-transport {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-color-primary, #409eff);
  line-height: 1.7;
}

/* ------------------------------ 住宿推荐与预算 ------------------------------ */
.stay-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.stay-panel {
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: 8px;
  padding: 14px 16px;
  background: var(--el-fill-color-blank, #fff);
  margin-bottom: 16px;
}
.stay-ctrl {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.stay-ctrl-label {
  flex: 0 0 auto;
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
}
.stay-calc {
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
  white-space: nowrap;
}
.stay-calc b {
  color: var(--el-color-primary, #409eff);
}
/* 不选用推荐酒店时，由用户自己填住宿花销 */
.custom-cost-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 0 0 12px;
  padding: 10px 12px;
  border: 1px dashed #f0d9b5;
  border-radius: 10px;
  background: #fffaf2;
}
.stay-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 10px;
  margin: 14px 0 12px;
}
.stay-stats .stat {
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light, #f5f7fa);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stay-stats .stat span {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.stay-stats .stat b {
  font-size: 17px;
  color: var(--el-text-color-primary, #303133);
}
.stay-stats .stat b.hl {
  color: #e6a23c;
}
.stay-stats .stat b.match {
  color: #409eff;
}
.stay-stats .stat.ok {
  background: var(--el-color-success-light-9, #f0f9eb);
}
.stay-stats .stat.ok b {
  color: #529b2e;
}
.stay-stats .stat.over {
  background: var(--el-color-danger-light-9, #fef0f0);
}
.stay-stats .stat.over b {
  color: #c45656;
}
.stay-alert {
  margin-top: 4px;
}
.stay-hint {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.7;
}

.hotel-item {
  padding: 12px 12px;
  border-bottom: 1px dashed var(--el-border-color-lighter, #ebeef5);
  border-left: 3px solid transparent;
  border-radius: 4px;
}
.hotel-item.is-selected {
  border-left-color: var(--el-color-warning, #e6a23c);
  background: var(--el-color-warning-light-9, #fdf6ec);
}
.hotel-item:last-of-type {
  border-bottom: none;
}
.hotel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.hotel-price {
  color: #e6a23c;
  font-weight: 600;
}
.stay-cost {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
.hotel-line {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  line-height: 1.7;
  color: var(--el-text-color-regular, #606266);
}

/* -------------------------------- 购物推荐 -------------------------------- */
.shop-item {
  padding: 12px 12px;
  border-bottom: 1px dashed var(--el-border-color-lighter, #ebeef5);
  border-left: 3px solid var(--el-color-warning, #e6a23c);
  border-radius: 4px;
}
.shop-item:last-of-type {
  border-bottom: none;
}
.shop-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ------------------------------ 住宿基点提示 ------------------------------ */
.stay-base {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  border-left: 3px solid var(--el-color-warning, #e6a23c);
  background: var(--el-color-warning-light-9, #fdf6ec);
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-regular, #606266);
}
.stay-base .el-icon {
  color: var(--el-color-warning, #e6a23c);
}

/* -------------------------------- 预算合计 -------------------------------- */
.total-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
}
.total-row b {
  color: var(--el-text-color-primary, #303133);
}

/* ============================ UI v1.1 新增样式 ============================ */

/* 顶部操作栏：左侧路径 + 右侧主次操作 */
.detail-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 16px;
}

.bar-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.bar-divider {
  width: 1px;
  height: 16px;
  background: var(--border);
}

.bar-title {
  font-size: var(--fs-label);
  color: var(--text-2);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bar-actions :deep(.el-button) {
  margin-left: 0;
}

.more-btn {
  width: 32px;
  padding: 0;
}

.danger-text {
  color: var(--danger);
}

/* 编辑态黄条：明确「未保存」 */
.edit-banner {
  background: #fdf6e7;
  border: 0.5px solid #f5dfb0;
  color: #8a5a00;
  font-size: var(--fs-note);
  border-radius: var(--radius-ctrl);
  padding: 8px 12px;
  margin-bottom: 16px;
}

/* 概览：标题 + 质检胶囊 */
.overview-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.quality-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  font-size: var(--fs-mini);
  white-space: nowrap;
}

.quality-pill .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.quality-pill.is-ok {
  background: #eaf7f1;
  border: 0.5px solid #c3e9da;
  color: #0f6e56;
}

.quality-pill.is-ok .dot {
  background: var(--success);
}

.quality-pill.is-warn {
  background: #fdf3e3;
  border: 0.5px solid #f2dcb6;
  color: #8a5a00;
}

.quality-pill.is-warn .dot {
  background: var(--warning);
}

/* 关键数字条 */
.metric-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin: 14px 0 4px;
  padding-top: 14px;
  border-top: 1px solid #eff2f7;
}

.metric-cell {
  min-width: 0;
}

.metric-value.amount {
  color: var(--accent-amount);
}

.metric-value.is-ok {
  color: var(--success);
}

.metric-value.is-over {
  color: var(--danger);
}

/* 概览键值：两列无边框，替代 el-descriptions 的表格线 */
.kv-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 24px;
  margin-top: 14px;
}

.kv-grid .kv {
  padding: 6px 0;
  border-bottom: 1px dashed var(--border);
}

.kv-grid .kv-label {
  flex: 0 0 60px;
  color: var(--text-3);
  font-size: var(--fs-note);
}

.kv-grid .kv-text {
  font-size: var(--fs-label);
  color: var(--text-1);
  line-height: 1.7;
}

/* 交通：时间轴行 */
.leg-list {
  display: flex;
  flex-direction: column;
}

.leg {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed #eff2f7;
}

.leg:last-child {
  border-bottom: none;
}

.leg-time {
  flex: 0 0 46px;
  text-align: right;
  font-size: var(--fs-note);
  color: var(--text-1);
  line-height: 1.6;
}

.leg-time span {
  display: block;
}

.leg-end {
  color: var(--text-3);
}

.leg-rail {
  flex: 0 0 9px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 4px;
}

.leg-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand);
  flex: none;
}

.leg-dot.hollow {
  background: #fff;
  border: 1.5px solid var(--brand);
}

.leg-line {
  flex: 1;
  width: 1px;
  background: #d3e2fb;
  margin: 3px 0;
}

.leg-body {
  flex: 1;
  min-width: 0;
}

.leg-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 3px;
}

.leg-code {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: var(--fs-mini);
  background: var(--brand-light);
  border: 0.5px solid #d3e2fb;
  color: var(--brand-active);
  font-variant-numeric: tabular-nums;
}

.leg-name {
  font-size: var(--fs-note);
  font-weight: 500;
  color: var(--text-1);
}

.leg-desc {
  font-size: var(--fs-note);
  color: var(--text-2);
  line-height: 1.7;
}

.leg-price {
  flex: 0 0 auto;
  text-align: right;
  font-size: var(--fs-label);
  font-weight: 500;
  color: var(--text-1);
}

.leg-seat {
  display: block;
  font-size: var(--fs-mini);
  color: var(--text-3);
  font-weight: 400;
  margin-top: 2px;
}

.local-transport {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  font-size: var(--fs-note);
  color: var(--text-2);
  line-height: 1.7;
}

.local-tag {
  flex: none;
  font-size: var(--fs-mini);
  color: var(--brand-active);
  background: var(--brand-light);
  border-radius: 4px;
  padding: 1px 6px;
}

.options-block {
  margin-top: 14px;
}

.options-block .plain-list {
  font-size: var(--fs-note);
  color: var(--text-2);
  line-height: 1.8;
}

/* 每日行程：时间列 + 主体 */
.item-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.item-time {
  flex: 0 0 54px;
  font-size: var(--fs-note);
  color: var(--text-2);
  line-height: 1.7;
}

.item-main {
  flex: 1;
  min-width: 0;
}

.item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.item-place {
  font-size: var(--fs-body);
  color: var(--text-1);
}

.item-activity {
  margin-top: 4px;
  font-size: var(--fs-label);
  color: var(--text-1);
  line-height: 1.7;
}

.item-tips {
  margin-top: 4px;
  font-size: var(--fs-mini);
  color: var(--text-3);
  line-height: 1.6;
}

.timeline-item {
  border-left: 2px solid var(--brand-light);
  padding: 10px 0 12px 14px;
}

/* 生成中骨架 */
.skeleton-wrap {
  padding-top: 4px;
}

.sk-hint {
  font-size: var(--fs-label);
  color: var(--text-2);
  margin: 0 0 14px;
}

.sk-card {
  padding: 18px 20px;
}

.sk-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .metric-strip,
  .sk-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
  .kv-grid {
    grid-template-columns: 1fr;
  }
  .bar-title {
    display: none;
  }
  .detail-bar {
    padding: 8px 12px;
  }
}
</style>
