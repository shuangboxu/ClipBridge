package handlers

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestShareHandlerPublicQRCodeReturnsPNG(t *testing.T) {
	handler := &ShareHandler{}

	request := httptest.NewRequest(
		http.MethodGet,
		"/v1/public/qrcode?content=https://clipbridge.example.com/%23/public/token-1&size=256",
		nil,
	)
	recorder := httptest.NewRecorder()

	handler.PublicQRCode(recorder, request)

	if recorder.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", recorder.Code)
	}
	if contentType := recorder.Header().Get("Content-Type"); contentType != "image/png" {
		t.Fatalf("expected png content type, got %s", contentType)
	}
	if len(recorder.Body.Bytes()) == 0 {
		t.Fatal("expected non-empty qrcode image body")
	}
}

func TestShareHandlerPublicQRCodeRejectsInvalidSize(t *testing.T) {
	handler := &ShareHandler{}

	request := httptest.NewRequest(
		http.MethodGet,
		"/v1/public/qrcode?content=hello&size=10",
		nil,
	)
	recorder := httptest.NewRecorder()

	handler.PublicQRCode(recorder, request)

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("expected status 400, got %d", recorder.Code)
	}
	if !strings.Contains(recorder.Body.String(), "size is out of range") {
		t.Fatalf("expected size range message, got %s", recorder.Body.String())
	}
}
