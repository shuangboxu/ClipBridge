package handlers

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"clipbridge/backend/internal/app"
	"clipbridge/backend/internal/config"
)

func TestRegisterReturnsForbiddenWhenRegistrationDisabled(t *testing.T) {
	handler := NewAuthHandler(&app.App{
		Config: config.Config{
			Auth: config.AuthConfig{
				AllowRegistration: false,
			},
		},
	})

	request := httptest.NewRequest(
		http.MethodPost,
		"/v1/auth/register",
		strings.NewReader(`{"username":"alice","password":"password123"}`),
	)
	request.Header.Set("Content-Type", "application/json")

	recorder := httptest.NewRecorder()
	handler.Register(recorder, request)

	if recorder.Code != http.StatusForbidden {
		t.Fatalf("expected status 403, got %d", recorder.Code)
	}

	var envelope map[string]any
	if err := json.Unmarshal(recorder.Body.Bytes(), &envelope); err != nil {
		t.Fatalf("unmarshal response failed: %v", err)
	}

	if envelope["message"] != "registration is disabled" {
		t.Fatalf("expected disabled registration message, got %v", envelope["message"])
	}
}

func TestGetRegistrationPolicyReturnsLatestAdminSetting(t *testing.T) {
	handler := NewAuthHandler(&app.App{
		Config: config.Config{
			Auth: config.AuthConfig{
				AllowRegistration: false,
			},
		},
	})
	handler.adminService = stubAuthAdminService{
		allowRegistration: true,
	}

	request := httptest.NewRequest(http.MethodGet, "/v1/auth/registration-policy", nil)
	recorder := httptest.NewRecorder()
	handler.GetRegistrationPolicy(recorder, request)

	if recorder.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", recorder.Code)
	}

	var envelope struct {
		Code int `json:"code"`
		Data struct {
			AllowRegistration bool `json:"allow_registration"`
		} `json:"data"`
	}
	if err := json.Unmarshal(recorder.Body.Bytes(), &envelope); err != nil {
		t.Fatalf("unmarshal response failed: %v", err)
	}

	if envelope.Code != 0 {
		t.Fatalf("expected business code 0, got %d", envelope.Code)
	}
	if !envelope.Data.AllowRegistration {
		t.Fatalf("expected allow_registration to be true")
	}
}

type stubAuthAdminService struct {
	allowRegistration bool
	err               error
}

func (s stubAuthAdminService) RegistrationAllowed(ctx context.Context) (bool, error) {
	return s.allowRegistration, s.err
}
