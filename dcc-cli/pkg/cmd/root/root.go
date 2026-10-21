// Package root builds the `dcc` command tree and owns process exit.
package root

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/config"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/application"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/audit"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/auth"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/completion"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/drift"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/environment"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/interfaceapi"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/migration"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/standard"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/table"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/template"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/version"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/iostreams"
)

// NewCmdRoot assembles the whole command tree. Commands never reach for global
// state: everything they need comes from the factory.
func NewCmdRoot(f *factory.Factory) *cobra.Command {
	root := &cobra.Command{
		Use:   "dcc",
		Short: "DCC command-line interface",
		Long: `Work with Data Contract Center artifacts from the terminal.

The CLI is a thin HTTP client over dcc-web; it holds no business logic.
Run "dcc auth login" once, then use the noun groups below.`,
		SilenceUsage:  true,
		SilenceErrors: true,
		Args:          cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			noColor, err := cmd.Flags().GetBool("no-color")
			if err != nil {
				return err
			}
			if noColor {
				f.IOStreams.SetColorEnabled(false)
			}
			return nil
		},
	}

	root.SetIn(f.IOStreams.In)
	root.SetOut(f.IOStreams.Out)
	root.SetErr(f.IOStreams.ErrOut)

	pf := root.PersistentFlags()
	pf.String("hostname", "", "Configured host to use (default: current host)")
	pf.String("server", "", "Backend base URL for this invocation (env: DCC_SERVER_URL)")
	pf.String("api-version", "", "Command API path version (default: v1)")
	pf.String("json", "", "Output JSON; optionally a comma-separated field list")
	pf.Lookup("json").NoOptDefVal = "true"
	pf.String("jq", "", "Filter JSON output with a jq expression")
	pf.String("template", "", "Render JSON output with a Go template")
	pf.Bool("no-color", false, "Disable colour output")

	root.AddCommand(
		auth.NewCmdAuth(f),
		standard.NewCmdStandard(f),
		table.NewCmdTable(f),
		interfaceapi.NewCmdInterface(f),
		template.NewCmdTemplate(f),
		audit.NewCmdAudit(f),
		migration.NewCmdMigration(f),
		environment.NewCmdEnvironment(f),
		application.NewCmdApplication(f),
		drift.NewCmdDrift(f),
		completion.NewCmdCompletion(f),
		version.NewCmdVersion(f),
	)
	return root
}

// Execute runs the CLI and returns the process exit code. Usage and auth
// failures map to 2 and 4; everything else is 1.
func Execute() int {
	f := &factory.Factory{
		IOStreams: iostreams.System(),
		Config:    config.Load,
	}

	cmd := NewCmdRoot(f)
	err := cmd.Execute()
	if err == nil {
		return 0
	}

	if msg := err.Error(); msg != "" {
		fmt.Fprintf(f.IOStreams.ErrOut, "dcc: %s\n", msg)
	}
	code := cmdutil.ExitCode(err)
	if code == 1 && strings.HasPrefix(err.Error(), "unknown command") {
		code = 2
	}
	return code
}
