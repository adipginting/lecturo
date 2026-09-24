package blob

import (
	"context"
	"fmt"
	"io"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

type s3Store struct {
	client *minio.Client
	bucket string
}

// New connects to an S3-compatible endpoint and ensures the bucket exists.
func New(ctx context.Context, cfg Config) (Store, error) {
	if cfg.Endpoint == "" {
		return nil, fmt.Errorf("blob: endpoint is required")
	}
	if cfg.Bucket == "" {
		return nil, fmt.Errorf("blob: bucket is required")
	}

	lookup := minio.BucketLookupPath
	if cfg.VirtualHost {
		lookup = minio.BucketLookupDNS
	}

	client, err := minio.New(cfg.Endpoint, &minio.Options{
		Creds:        credentials.NewStaticV4(cfg.AccessKey, cfg.SecretKey, ""),
		Secure:       cfg.UseSSL,
		Region:       cfg.Region,
		BucketLookup: lookup,
	})
	if err != nil {
		return nil, fmt.Errorf("blob: client: %w", err)
	}

	exists, err := client.BucketExists(ctx, cfg.Bucket)
	if err != nil {
		return nil, fmt.Errorf("blob: check bucket: %w", err)
	}
	if !exists {
		if err := client.MakeBucket(ctx, cfg.Bucket, minio.MakeBucketOptions{Region: cfg.Region}); err != nil {
			return nil, fmt.Errorf("blob: create bucket: %w", err)
		}
	}

	return &s3Store{client: client, bucket: cfg.Bucket}, nil
}

func (s *s3Store) Put(ctx context.Context, key string, r io.Reader, size int64, contentType string) error {
	_, err := s.client.PutObject(ctx, s.bucket, key, r, size, minio.PutObjectOptions{ContentType: contentType})
	if err != nil {
		return fmt.Errorf("blob: put %s: %w", key, err)
	}
	return nil
}

func (s *s3Store) Get(ctx context.Context, key string) (io.ReadCloser, error) {
	obj, err := s.client.GetObject(ctx, s.bucket, key, minio.GetObjectOptions{})
	if err != nil {
		return nil, fmt.Errorf("blob: get %s: %w", key, err)
	}
	// GetObject is lazy; surface not-found here rather than at first read.
	if _, err := obj.Stat(); err != nil {
		_ = obj.Close()
		return nil, fmt.Errorf("blob: get %s: %w", key, err)
	}
	return obj, nil
}

func (s *s3Store) Delete(ctx context.Context, key string) error {
	if err := s.client.RemoveObject(ctx, s.bucket, key, minio.RemoveObjectOptions{}); err != nil {
		return fmt.Errorf("blob: delete %s: %w", key, err)
	}
	return nil
}
