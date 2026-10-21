package artifact

import (
	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

// Attach adds the shared verb set to a noun command.
func Attach(parent *cobra.Command, f *factory.Factory, kind Kind) {
	parent.AddCommand(
		NewCmdList(f, kind),
		NewCmdView(f, kind),
		NewCmdHistory(f, kind),
		NewCmdDiff(f, kind),
		NewCmdVerify(f, kind),
		NewCmdHash(f, kind),
		NewCmdBump(f, kind),
		NewCmdValidate(f, kind),
	)
	if kind.ExportFormat != "" {
		parent.AddCommand(NewCmdExport(f, kind))
	}
	if kind.SupportsDDL {
		parent.AddCommand(NewCmdDDL(f, kind), NewCmdDDLAlter(f, kind))
	}
	parent.AddCommand(
		NewCmdCreate(f, kind),
		NewCmdEdit(f, kind),
		NewCmdStatus(f, kind),
		NewCmdRollback(f, kind),
		NewCmdDelete(f, kind),
		NewCmdRestore(f, kind),
	)
}
