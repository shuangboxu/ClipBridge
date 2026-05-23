package handlers

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
)

var errEmptyJSONBody = errors.New("request body is empty")

func decodeJSONBody(r *http.Request, dst any) error {
	if r.Body == nil {
		return errEmptyJSONBody
	}

	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()

	if err := decoder.Decode(dst); err != nil {
		if errors.Is(err, io.EOF) {
			return errEmptyJSONBody
		}
		return fmt.Errorf("decode json body failed: %w", err)
	}

	var extra json.RawMessage
	if err := decoder.Decode(&extra); err != io.EOF {
		return errors.New("request body must contain a single json object")
	}
	return nil
}
