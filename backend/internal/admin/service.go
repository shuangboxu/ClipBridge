package admin

import (
	"context"
	"fmt"
	"io"
	"strings"
)

type Service struct {
	repo      Repository
	bandwidth *BandwidthManager
	cleaner   PathCleaner
}

func NewService(repo Repository, bandwidth *BandwidthManager, cleaner PathCleaner) *Service {
	return &Service{
		repo:      repo,
		bandwidth: bandwidth,
		cleaner:   cleaner,
	}
}

func (s *Service) SeedSystemSettings(ctx context.Context, seed SeedSettings) error {
	if s == nil || s.repo == nil {
		return fmt.Errorf("admin service is not ready")
	}
	_, err := s.repo.EnsureSystemSettings(ctx, normalizeSeedSettings(seed))
	return err
}

func (s *Service) RegistrationAllowed(ctx context.Context) (bool, error) {
	if s == nil || s.repo == nil {
		return false, fmt.Errorf("admin service is not ready")
	}

	settings, err := s.repo.GetSystemSettings(ctx)
	if err != nil {
		return false, err
	}
	return settings.AllowRegistration, nil
}

func (s *Service) CurrentMaxUploadBytes(ctx context.Context, userID string) (int64, error) {
	if s == nil || s.repo == nil {
		return 0, fmt.Errorf("admin service is not ready")
	}

	policy, err := s.repo.GetUserTransferPolicy(ctx, strings.TrimSpace(userID))
	if err != nil {
		return 0, err
	}
	return policy.MaxUploadFileBytes, nil
}

func (s *Service) PrepareUploadReader(ctx context.Context, userID string, src io.Reader) (io.Reader, int64, error) {
	if s == nil || s.repo == nil {
		return nil, 0, fmt.Errorf("admin service is not ready")
	}
	if src == nil {
		return nil, 0, fmt.Errorf("upload reader is required")
	}

	policy, err := s.repo.GetUserTransferPolicy(ctx, strings.TrimSpace(userID))
	if err != nil {
		return nil, 0, err
	}

	reader := src
	if s.bandwidth != nil {
		reader = s.bandwidth.WrapUploadReader(ctx, userID, int64(policy.UploadBandwidthKbps), src)
	}
	return reader, policy.MaxUploadFileBytes, nil
}

func (s *Service) PrepareDownloadWriter(ctx context.Context, userID string, dst io.Writer) (io.Writer, error) {
	if s == nil || s.repo == nil {
		return nil, fmt.Errorf("admin service is not ready")
	}
	if dst == nil {
		return nil, fmt.Errorf("download writer is required")
	}

	policy, err := s.repo.GetUserTransferPolicy(ctx, strings.TrimSpace(userID))
	if err != nil {
		return nil, err
	}

	writer := dst
	if s.bandwidth != nil {
		writer = s.bandwidth.WrapDownloadWriter(ctx, userID, int64(policy.DownloadBandwidthKbps), dst)
	}
	return writer, nil
}

func (s *Service) GetAccountOverview(ctx context.Context, userID string) (AccountOverview, error) {
	if s == nil || s.repo == nil {
		return AccountOverview{}, fmt.Errorf("admin service is not ready")
	}
	return s.repo.GetAccountOverview(ctx, strings.TrimSpace(userID))
}

func (s *Service) GetSettings(ctx context.Context) (SystemSettings, error) {
	if s == nil || s.repo == nil {
		return SystemSettings{}, fmt.Errorf("admin service is not ready")
	}
	return s.repo.GetSystemSettings(ctx)
}

func (s *Service) CountUsers(ctx context.Context) (int, error) {
	if s == nil || s.repo == nil {
		return 0, fmt.Errorf("admin service is not ready")
	}
	return s.repo.CountUsers(ctx)
}

func (s *Service) UpdateSettings(ctx context.Context, input UpdateSettingsInput) (SystemSettings, error) {
	if s == nil || s.repo == nil {
		return SystemSettings{}, fmt.Errorf("admin service is not ready")
	}
	return s.repo.UpdateSystemSettings(ctx, input)
}

func (s *Service) CreateQuotaRequest(ctx context.Context, userID string, requestedQuotaBytes int64, reason string) (QuotaRequest, error) {
	if s == nil || s.repo == nil {
		return QuotaRequest{}, fmt.Errorf("admin service is not ready")
	}
	if requestedQuotaBytes <= 0 {
		return QuotaRequest{}, ErrInvalidArgument
	}
	return s.repo.CreateQuotaRequest(ctx, strings.TrimSpace(userID), requestedQuotaBytes, strings.TrimSpace(reason))
}

func (s *Service) ListMyQuotaRequests(ctx context.Context, userID, status string) ([]QuotaRequest, error) {
	if s == nil || s.repo == nil {
		return nil, fmt.Errorf("admin service is not ready")
	}
	return s.repo.ListQuotaRequestsByUser(ctx, strings.TrimSpace(userID), normalizeRequestStatusFilter(status))
}

func (s *Service) CreateBandwidthRequest(ctx context.Context, userID string, requestedUploadKbps, requestedDownloadKbps int, reason string) (BandwidthRequest, error) {
	if s == nil || s.repo == nil {
		return BandwidthRequest{}, fmt.Errorf("admin service is not ready")
	}
	if requestedUploadKbps <= 0 || requestedDownloadKbps <= 0 {
		return BandwidthRequest{}, ErrInvalidArgument
	}
	return s.repo.CreateBandwidthRequest(ctx, strings.TrimSpace(userID), requestedUploadKbps, requestedDownloadKbps, strings.TrimSpace(reason))
}

func (s *Service) ListMyBandwidthRequests(ctx context.Context, userID, status string) ([]BandwidthRequest, error) {
	if s == nil || s.repo == nil {
		return nil, fmt.Errorf("admin service is not ready")
	}
	return s.repo.ListBandwidthRequestsByUser(ctx, strings.TrimSpace(userID), normalizeRequestStatusFilter(status))
}

func (s *Service) CreateAdminRequest(ctx context.Context, userID, reason string) (AdminRequest, error) {
	if s == nil || s.repo == nil {
		return AdminRequest{}, fmt.Errorf("admin service is not ready")
	}
	return s.repo.CreateAdminRequest(ctx, strings.TrimSpace(userID), strings.TrimSpace(reason))
}

func (s *Service) ListMyAdminRequests(ctx context.Context, userID, status string) ([]AdminRequest, error) {
	if s == nil || s.repo == nil {
		return nil, fmt.Errorf("admin service is not ready")
	}
	return s.repo.ListAdminRequestsByUser(ctx, strings.TrimSpace(userID), normalizeRequestStatusFilter(status))
}

func (s *Service) ListUsers(ctx context.Context) ([]UserSummary, error) {
	if s == nil || s.repo == nil {
		return nil, fmt.Errorf("admin service is not ready")
	}
	return s.repo.ListUsers(ctx)
}

func (s *Service) UpdateUser(ctx context.Context, userID string, input UpdateUserInput) (UserSummary, error) {
	if s == nil || s.repo == nil {
		return UserSummary{}, fmt.Errorf("admin service is not ready")
	}
	return s.repo.UpdateUser(ctx, strings.TrimSpace(userID), input)
}

func (s *Service) DeleteUser(ctx context.Context, userID string) (DeleteUserResult, error) {
	if s == nil || s.repo == nil {
		return DeleteUserResult{}, fmt.Errorf("admin service is not ready")
	}

	result, err := s.repo.DeleteUser(ctx, strings.TrimSpace(userID))
	if err != nil {
		return DeleteUserResult{}, err
	}

	if s.cleaner != nil {
		seen := make(map[string]struct{}, len(result.StoredPaths))
		for _, storedPath := range result.StoredPaths {
			storedPath = strings.TrimSpace(storedPath)
			if storedPath == "" {
				continue
			}
			if _, ok := seen[storedPath]; ok {
				continue
			}
			seen[storedPath] = struct{}{}
			_ = s.cleaner.Delete(storedPath)
		}
	}

	return result, nil
}

func (s *Service) ListQuotaRequestsForAdmin(ctx context.Context, status string) ([]QuotaRequest, error) {
	if s == nil || s.repo == nil {
		return nil, fmt.Errorf("admin service is not ready")
	}
	return s.repo.ListQuotaRequestsForAdmin(ctx, normalizeRequestStatusFilter(status))
}

func (s *Service) ApproveQuotaRequest(ctx context.Context, requestID, reviewerID string, input ApproveQuotaRequestInput) (QuotaRequest, error) {
	if s == nil || s.repo == nil {
		return QuotaRequest{}, fmt.Errorf("admin service is not ready")
	}
	if input.ApprovedQuotaBytes != nil && *input.ApprovedQuotaBytes <= 0 {
		return QuotaRequest{}, ErrInvalidArgument
	}
	input.ReviewNote = strings.TrimSpace(input.ReviewNote)
	return s.repo.ApproveQuotaRequest(ctx, strings.TrimSpace(requestID), strings.TrimSpace(reviewerID), input)
}

func (s *Service) RejectQuotaRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (QuotaRequest, error) {
	if s == nil || s.repo == nil {
		return QuotaRequest{}, fmt.Errorf("admin service is not ready")
	}
	input.ReviewNote = strings.TrimSpace(input.ReviewNote)
	return s.repo.RejectQuotaRequest(ctx, strings.TrimSpace(requestID), strings.TrimSpace(reviewerID), input)
}

func (s *Service) ListBandwidthRequestsForAdmin(ctx context.Context, status string) ([]BandwidthRequest, error) {
	if s == nil || s.repo == nil {
		return nil, fmt.Errorf("admin service is not ready")
	}
	return s.repo.ListBandwidthRequestsForAdmin(ctx, normalizeRequestStatusFilter(status))
}

func (s *Service) ApproveBandwidthRequest(ctx context.Context, requestID, reviewerID string, input ApproveBandwidthRequestInput) (BandwidthRequest, error) {
	if s == nil || s.repo == nil {
		return BandwidthRequest{}, fmt.Errorf("admin service is not ready")
	}
	if input.ApprovedUploadKbps != nil && *input.ApprovedUploadKbps <= 0 {
		return BandwidthRequest{}, ErrInvalidArgument
	}
	if input.ApprovedDownloadKbps != nil && *input.ApprovedDownloadKbps <= 0 {
		return BandwidthRequest{}, ErrInvalidArgument
	}
	input.ReviewNote = strings.TrimSpace(input.ReviewNote)
	return s.repo.ApproveBandwidthRequest(ctx, strings.TrimSpace(requestID), strings.TrimSpace(reviewerID), input)
}

func (s *Service) RejectBandwidthRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (BandwidthRequest, error) {
	if s == nil || s.repo == nil {
		return BandwidthRequest{}, fmt.Errorf("admin service is not ready")
	}
	input.ReviewNote = strings.TrimSpace(input.ReviewNote)
	return s.repo.RejectBandwidthRequest(ctx, strings.TrimSpace(requestID), strings.TrimSpace(reviewerID), input)
}

func (s *Service) ListAdminRequestsForAdmin(ctx context.Context, status string) ([]AdminRequest, error) {
	if s == nil || s.repo == nil {
		return nil, fmt.Errorf("admin service is not ready")
	}
	return s.repo.ListAdminRequestsForAdmin(ctx, normalizeRequestStatusFilter(status))
}

func (s *Service) ApproveAdminRequest(ctx context.Context, requestID, reviewerID string, input ApproveAdminRequestInput) (AdminRequest, error) {
	if s == nil || s.repo == nil {
		return AdminRequest{}, fmt.Errorf("admin service is not ready")
	}
	input.ReviewNote = strings.TrimSpace(input.ReviewNote)
	return s.repo.ApproveAdminRequest(ctx, strings.TrimSpace(requestID), strings.TrimSpace(reviewerID), input)
}

func (s *Service) RejectAdminRequest(ctx context.Context, requestID, reviewerID string, input RejectRequestInput) (AdminRequest, error) {
	if s == nil || s.repo == nil {
		return AdminRequest{}, fmt.Errorf("admin service is not ready")
	}
	input.ReviewNote = strings.TrimSpace(input.ReviewNote)
	return s.repo.RejectAdminRequest(ctx, strings.TrimSpace(requestID), strings.TrimSpace(reviewerID), input)
}

func normalizeSeedSettings(seed SeedSettings) SeedSettings {
	if seed.MaxUserCount <= 0 {
		seed.MaxUserCount = DefaultMaxUserCount
	}
	if seed.DefaultStorageQuotaBytes <= 0 {
		seed.DefaultStorageQuotaBytes = DefaultStorageQuotaBytes
	}
	if seed.DefaultUploadBandwidthKbps <= 0 {
		seed.DefaultUploadBandwidthKbps = DefaultUploadBandwidthKbps
	}
	if seed.DefaultDownloadBandwidthKbps <= 0 {
		seed.DefaultDownloadBandwidthKbps = DefaultDownloadBandwidthKbps
	}
	if seed.MaxUserUploadBandwidthKbps <= 0 {
		seed.MaxUserUploadBandwidthKbps = DefaultMaxUserUploadBandwidthKbps
	}
	if seed.MaxUserDownloadBandwidthKbps <= 0 {
		seed.MaxUserDownloadBandwidthKbps = DefaultMaxUserDownloadBandwidthKbps
	}
	if seed.MaxUploadFileBytes <= 0 {
		seed.MaxUploadFileBytes = 64 * 1024 * 1024
	}
	return seed
}

func normalizeRequestStatusFilter(raw string) string {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "", StatusAll:
		return StatusAll
	case StatusPending:
		return StatusPending
	case StatusApproved:
		return StatusApproved
	case StatusRejected:
		return StatusRejected
	default:
		return StatusAll
	}
}
