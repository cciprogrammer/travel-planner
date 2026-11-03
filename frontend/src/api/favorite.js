import request from '@/utils/request'

export function fetchFavorites() {
  return request.get('/favorites')
}

export function addFavorite(data) {
  return request.post('/favorites', data)
}

export function deleteFavorite(id) {
  return request.delete(`/favorites/${id}`)
}
