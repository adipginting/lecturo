// Package skills is the domain model for installed skills and their runs. A
// skill is a named prompt with a manifest, run against a document.
package skills

import "time"

// RemoteSkill is one entry in the marketplace.
type RemoteSkill struct {
	SkillID     string
	Name        string
	Description string
	Author      string
	Version     string
	Scope       string
	Trigger     string
	Prompt      string
	Price       string
}

// IsPaid reports whether the marketplace marks the skill as paid.
func (s RemoteSkill) IsPaid() bool { return s.Price != "" && s.Price != "free" }

// Skill is an installed skill.
type Skill struct {
	ID          string
	SkillID     string
	Name        string
	Description string
	Author      string
	Version     string
	Scope       string
	Trigger     string
	Prompt      string
	Price       string
	Enabled     bool
}

// RunStatus is how a skill run ended, or that it has not ended yet.
type RunStatus string

const (
	RunRunning RunStatus = "running"
	RunDone    RunStatus = "done"
	RunFailed  RunStatus = "failed"
)

// SkillRun is one run of a skill against a document.
type SkillRun struct {
	ID         string
	SkillID    string
	SkillName  string
	DocumentID *string
	Status     RunStatus
	Result     string
	Error      string
	CreatedAt  time.Time
}
