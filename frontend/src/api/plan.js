import request from '@/utils/request'

export function generatePlan(data) {
  return request.post('/plans/generate', data)
}

export function regeneratePlan(id) {
  return request.post(`/plans/${id}/regenerate`)
}

export function fetchPlans(params) {
  return request.get('/plans', { params })
}

export function fetchPlan(id) {
  return request.get(`/plans/${id}`)
}

export function updatePlan(id, data) {
  return request.put(`/plans/${id}`, data)
}

export function deletePlan(id) {
  return request.delete(`/plans/${id}`)
}
