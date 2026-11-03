import request from '@/utils/request'

/* ------------------------------ 数据概览 ------------------------------ */

export function fetchStats() {
  return request.get('/admin/stats')
}

/* ------------------------------ 用户数据 ------------------------------ */

export function fetchUsers(params) {
  return request.get('/admin/users', { params })
}

export function updateUserStatus(id, status) {
  return request.put(`/admin/users/${id}/status`, null, { params: { status } })
}

export function fetchUserPlans(id, params) {
  return request.get(`/admin/users/${id}/plans`, { params })
}

/* ------------------------------ 酒店管理 ------------------------------ */

export function fetchHotels(params) {
  return request.get('/admin/hotels', { params })
}

export function fetchHotelCities() {
  return request.get('/admin/hotels/cities')
}

export function createHotel(data) {
  return request.post('/admin/hotels', data)
}

export function updateHotel(id, data) {
  return request.put(`/admin/hotels/${id}`, data)
}

export function updateHotelStatus(id, status) {
  return request.put(`/admin/hotels/${id}/status`, null, { params: { status } })
}

export function deleteHotel(id) {
  return request.delete(`/admin/hotels/${id}`)
}
