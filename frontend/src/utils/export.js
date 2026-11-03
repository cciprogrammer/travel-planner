/**
 * 行程导出工具：Markdown / 打印（可另存 PDF）
 */

function cell(text) {
  return String(text ?? '').replace(/\|/g, '\\|').replace(/\n/g, ' ')
}

/** 从「约 800 元（…）」里取第一个数字 */
function money(text) {
  const m = String(text == null ? '' : text).match(/\d+(?:\.\d+)?/g)
  return m && m.length ? Number(m[0]) : 0
}

/** 从「420-560 元/晚」解析每晚单价（取区间中值），解析不出时回退 pricePerNight */
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

/**
 * 汇总住宿预算：每晚预算 × 晚数，与所选酒店实际花费的差额只作为结余提示。
 * 注意：不设任何购物额度，购物消费完全由用户自行决定。
 */
function staySummary(plan, it) {
  const stay = it.stay || {}
  const hotels = it.hotels || []
  const nights = Number(stay.nights) > 0 ? Number(stay.nights) : Math.max(1, (Number(plan.days) || 1) - 1)
  const selected = hotels.find((h) => h.name === stay.selectedHotel) || null
  // 用户在地图上自选了住宿并填了花销 → 以自填金额为准（不选用推荐酒店的情况）
  const customPrice = Number(stay.customPricePerNight) || 0
  const customName = (stay.customLocation && stay.customLocation.name) || ''
  const effective = customPrice > 0 ? customPrice : (selected ? hotelPrice(selected) : 0)
  const perNight = Number(stay.budgetPerNight) > 0 ? Number(stay.budgetPerNight) : effective
  const budget = perNight * nights
  const cost = effective * nights
  const surplus = budget - cost
  // 预算内可选的酒店（预算为 0 时视为不限）
  const withinCount = perNight > 0
    ? hotels.filter((h) => hotelPrice(h) > 0 && hotelPrice(h) <= perNight).length
    : hotels.length
  return { nights, selected, perNight, budget, cost, surplus, withinCount, customPrice, customName }
}

export function itineraryToMarkdown(plan) {
  const it = plan.itinerary || {}
  let md = `# ${plan.title || '旅行行程方案'}\n\n`

  md += `> 出发地：**${plan.origin || '-'}** ｜ 目的地：**${plan.destination || '-'}** ｜ 天数：**${plan.days ?? '-'} 天** ｜ 预算：**${plan.budget ?? '-'} 元**\n\n`

  if (it.summary) {
    md += `## 行程总览\n\n${it.summary}\n\n`
  }

  // ---------------- 交通安排 ----------------
  if (it.transport) {
    md += `## 交通安排\n\n`
    md += `| 项目 | 说明 |\n| --- | --- |\n`
    md += `| 去程 | ${cell(it.transport.outbound)} |\n`
    md += `| 返程 | ${cell(it.transport.inbound)} |\n`
    md += `| 市内交通 | ${cell(it.transport.local)} |\n\n`
    if (it.transport.options && it.transport.options.length) {
      md += `**可选交通方式**\n\n`
      it.transport.options.forEach((o) => {
        md += `- ${o}\n`
      })
      md += `\n`
    }
  }

  // ---------------- 住宿推荐与预算（紧随交通安排） ----------------
  const stay = staySummary(plan, it)
  if (it.hotels && it.hotels.length) {
    md += `## 住宿推荐与预算\n\n`
    md += `- **入住晚数**：${stay.nights} 晚\n`
    md += `- **每晚住宿预算**：¥${stay.perNight}\n`
    md += `- **住宿预算合计**：¥${stay.budget}\n`
    if (stay.selected) {
      md += `- **当前选定**：${stay.selected.name}（¥${hotelPrice(stay.selected)}/晚 × ${stay.nights} 晚 = **¥${stay.cost}**）\n`
    }
    if (stay.customPrice > 0) {
      md += `- **自选住宿**：${stay.customName || '地图选点'}（自填 ¥${stay.customPrice}/晚 × ${stay.nights} 晚 = **¥${stay.cost}**）\n`
    }
    if (stay.surplus > 0) {
      md += `- **住宿结余**：¥${stay.surplus}（未用完的住宿预算，不预划购物额度）\n`
    } else if (stay.surplus < 0) {
      md += `- **超出住宿预算**：¥${-stay.surplus}（可切换更经济的酒店或上调每晚预算）\n`
    } else {
      md += `- **住宿结余**：¥0（预算刚好用尽）\n`
    }
    md += `- **预算内可选酒店**：${stay.withinCount} / ${it.hotels.length} 家（切换酒店后住宿与合计实时重算）\n\n`
    it.hotels.forEach((h, i) => {
      const mark = stay.selected && stay.selected.name === h.name ? ' · **当前住宿**' : ''
      const fit = stay.perNight > 0 && hotelPrice(h) > stay.perNight ? ' · 超出当前预算' : ''
      md += `### ${i + 1}. ${h.name || '酒店'}（${h.category || '备选'}${h.level ? ' · ' + h.level : ''}）${mark}${fit}\n\n`
      md += `- **所在区域**：${h.area || '-'}\n`
      md += `- **大致价位**：${h.price || '-'}${hotelPrice(h) ? `（按 ¥${hotelPrice(h)}/晚 计，${stay.nights} 晚共 ¥${hotelPrice(h) * stay.nights}）` : ''}\n`
      md += `- **推荐理由**：${h.reason || '-'}\n`
      md += `- **位置优势**：${h.advantage || '-'}\n\n`
    })
  }

  // ---------------- 购物推荐（只推荐去处，不设额度） ----------------
  if (it.shopping && it.shopping.length) {
    md += `## 购物推荐\n\n`
    md += `> 以下仅为值得一逛的地点与区域，**不设任何消费额度、不计入行程预算**。是否购物、花多少完全由你自行决定。\n\n`
    it.shopping.forEach((s, i) => {
      md += `### ${i + 1}. ${s.name || '购物地点'}${s.type ? `（${s.type}）` : ''}\n\n`
      if (s.area) {
        md += `- **所在区域**：${s.area}\n`
      }
      md += `- **适合买**：${s.buyWhat || '-'}\n`
      if (s.suitableFor) {
        md += `- **适合人群**：${s.suitableFor}\n`
      }
      if (s.tips) {
        md += `- **实用提示**：${s.tips}\n`
      }
      md += `\n`
    })
  }

  // ---------------- 每日安排 ----------------
  if (it.days && it.days.length) {
    md += `## 每日安排\n\n`
    if (stay.selected) {
      md += `> 每日动线以「${stay.selected.name}」为出发点与返回点。\n\n`
    }
    it.days.forEach((day) => {
      md += `### 第 ${day.day} 天 · ${day.theme || '行程'}${day.date ? `（${day.date}）` : ''}\n\n`
      if (day.transport) {
        md += `> **当天交通**：${day.transport}\n\n`
      }
      if (day.items && day.items.length) {
        md += `| 时间 | 地点 | 安排 | 交通方式 | 预估花费 | 贴士 |\n| --- | --- | --- | --- | --- | --- |\n`
        day.items.forEach((item) => {
          md += `| ${cell(item.time)} | ${cell(item.place)} | ${cell(item.activity)} | ${cell(item.transport)} | ${cell(item.cost)} | ${cell(item.tips)} |\n`
        })
      } else {
        md += `（暂无安排）\n`
      }
      md += `\n`
    })
  }

  // ---------------- 预算分配 ----------------
  if (it.budget) {
    const b = it.budget
    const other = money(b.transport) + money(b.food) + money(b.tickets) + money(b.other)
    const total = other + stay.cost
    md += `## 预算分配\n\n`
    md += `> 购物不设额度、不计入合计；住宿金额随所选酒店实时更新。\n\n`
    md += `| 交通 | 住宿 | 餐饮 | 门票 | 其他 | 合计 |\n| --- | --- | --- | --- | --- | --- |\n`
    md += `| ${cell(b.transport)} | ¥${stay.cost}（${stay.nights} 晚） | ${cell(b.food)} | ${cell(b.tickets)} | ${cell(b.other)} | **¥${total}** |\n\n`
  }

  // ---------------- 出行提示 ----------------
  if (it.tips && it.tips.length) {
    md += `## 出行提示\n\n`
    it.tips.forEach((t) => {
      md += `- ${t}\n`
    })
    md += `\n`
  }

  // ---------------- 标准校验 ----------------
  if (it.quality) {
    md += `## 行程标准校验\n\n`
    md += `- 结果：**${it.quality.passed ? '通过' : '未通过'}**（达标率 ${it.quality.score}%，未达标 ${it.quality.issueCount} 项）\n`
    if (it.quality.source) {
      md += `- 数据来源：${it.quality.source}\n`
    }
    if (it.quality.issues && it.quality.issues.length) {
      md += `\n| 级别 | 规则 | 位置 | 说明 |\n| --- | --- | --- | --- |\n`
      it.quality.issues.forEach((i) => {
        md += `| ${cell(i.level)} | ${cell(i.rule + ' ' + i.name)} | ${cell(i.target)} | ${cell(i.detail)} |\n`
      })
    }
    md += `\n`
  }

  md += `---\n\n由「基于大模型的旅行规划助手」自动生成，可人工二次编辑。\n`
  return md
}

export function downloadText(filename, text) {
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
