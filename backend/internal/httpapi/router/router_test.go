package router

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestSimpleRouterMatchesPathParameter(t *testing.T) {
	router := newSimpleRouter()
	router.Handle(http.MethodGet, "/v1/files/:id/download", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(r.PathValue("id")))
	}))

	request := httptest.NewRequest(http.MethodGet, "/v1/files/file-123/download", nil)
	recorder := httptest.NewRecorder()
	router.ServeHTTP(recorder, request)

	if recorder.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", recorder.Code)
	}
	if recorder.Body.String() != "file-123" {
		t.Fatalf("expected file id from path, got %q", recorder.Body.String())
	}
}

func TestSimpleRouterReturnsMethodNotAllowedForMatchedPath(t *testing.T) {
	router := newSimpleRouter()
	router.Handle(http.MethodGet, "/v1/files/:id", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))

	request := httptest.NewRequest(http.MethodDelete, "/v1/files/file-123", nil)
	recorder := httptest.NewRecorder()
	router.ServeHTTP(recorder, request)

	if recorder.Code != http.StatusMethodNotAllowed {
		t.Fatalf("expected status 405, got %d", recorder.Code)
	}
}
