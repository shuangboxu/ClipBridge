package handlers

import (
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
