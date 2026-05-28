package realtime

import (
	"encoding/json"
	"sync"
)

type Client struct {
	UserID   string
	DeviceID string
	Send     chan []byte
}

func NewClient(userID, deviceID string) *Client {
	return &Client{
		UserID:   userID,
		DeviceID: deviceID,
		// 这里保留一个小缓冲，避免瞬时广播把慢连接直接拖死。
		Send: make(chan []byte, 16),
	}
}

type Hub struct {
	mu      sync.RWMutex
	clients map[string]map[string]map[*Client]struct{}
}

func NewHub() *Hub {
	return &Hub{
		clients: make(map[string]map[string]map[*Client]struct{}),
	}
}

func (h *Hub) Register(client *Client) {
	if h == nil || client == nil {
		return
	}

	h.mu.Lock()
	defer h.mu.Unlock()

	if h.clients[client.UserID] == nil {
		h.clients[client.UserID] = make(map[string]map[*Client]struct{})
	}
	if h.clients[client.UserID][client.DeviceID] == nil {
		h.clients[client.UserID][client.DeviceID] = make(map[*Client]struct{})
	}
	h.clients[client.UserID][client.DeviceID][client] = struct{}{}
}

func (h *Hub) Unregister(client *Client) {
	if h == nil || client == nil {
		return
	}

	h.mu.Lock()
	defer h.mu.Unlock()

	deviceClients := h.clients[client.UserID][client.DeviceID]
	delete(deviceClients, client)
	close(client.Send)

	if len(deviceClients) == 0 {
		delete(h.clients[client.UserID], client.DeviceID)
	}
	if len(h.clients[client.UserID]) == 0 {
		delete(h.clients, client.UserID)
	}
}

func (h *Hub) SendToDevice(userID, deviceID string, payload any) {
	if h == nil {
		return
	}

	data, err := json.Marshal(payload)
	if err != nil {
		return
	}

	h.mu.RLock()
	deviceClients := h.clients[userID][deviceID]
	targets := make([]*Client, 0, len(deviceClients))
	for client := range deviceClients {
		targets = append(targets, client)
	}
	h.mu.RUnlock()

	h.dispatch(targets, data)
}

func (h *Hub) BroadcastToUserExcept(userID, excludedDeviceID string, payload any) {
	if h == nil {
		return
	}

	data, err := json.Marshal(payload)
	if err != nil {
		return
	}

	h.mu.RLock()
	targets := make([]*Client, 0)
	for deviceID, deviceClients := range h.clients[userID] {
		if deviceID == excludedDeviceID {
			continue
		}
		for client := range deviceClients {
			targets = append(targets, client)
		}
	}
	h.mu.RUnlock()

	h.dispatch(targets, data)
}

func (h *Hub) dispatch(targets []*Client, data []byte) {
	for _, client := range targets {
		select {
		case client.Send <- data:
		default:
			// 如果某个连接长期不消费，就直接清掉，
			// 避免整个实时通道被单个坏连接拖住。
			h.Unregister(client)
		}
	}
}
