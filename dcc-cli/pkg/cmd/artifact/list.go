package artifact

import (
	"io"
	"net/url"
	"strconv"

	"github.com/spf13/cobra"

	"github.com/open-banking-labs/dcc/dcc-cli/internal/cmdutil"
	"github.com/open-banking-labs/dcc/dcc-cli/internal/output"
	"github.com/open-banking-labs/dcc/dcc-cli/pkg/cmd/factory"
)

type listOptions struct {
	EnvironmentID int64
	ApplicationID int64
	Status        string
	Keyword       string
	Category      string
	Page          int
	Size          int
}

// NewCmdList returns the noun's list command.
func NewCmdList(f *factory.Factory, kind Kind) *cobra.Command {
	opts := &listOptions{Size: 20}
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List " + kind.Noun + "s",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			out, err := output.FromCommand(cmd)
			if err != nil {
				return err
			}
			client, _, err := cmdutil.NewClient(cmd, f)
			if err != nil {
				return err
			}

			query := url.Values{}
			if opts.EnvironmentID > 0 {
				query.Set("environmentId", strconv.FormatInt(opts.EnvironmentID, 10))
			}
			if opts.ApplicationID > 0 {
				query.Set("applicationId", strconv.FormatInt(opts.ApplicationID, 10))
			}
			if opts.Status != "" {
				query.Set("status", opts.Status)
			}

			path := kind.APIPath
			if kind.PageList {
				path += "/page"
				query.Set("page", strconv.Itoa(opts.Page))
				query.Set("size", strconv.Itoa(opts.Size))
				if opts.Keyword != "" {
					query.Set("keyword", opts.Keyword)
				}
				if opts.Category != "" {
					query.Set("category", opts.Category)
				}
			}

			var response any
			if err := client.Get(cmd.Context(), path, query, &response); err != nil {
				return cmdutil.WrapError(err)
			}
			return out.Print(f.IOStreams.Out, response, func(w io.Writer) error {
				return renderTable(w, f.IOStreams.IsStdoutTTY(), kind.ListColumns, extractItems(response, kind.PageList))
			})
		},
	}

	cmd.Flags().Int64Var(&opts.EnvironmentID, "environment-id", 0, "Filter by environment id")
	cmd.Flags().Int64Var(&opts.ApplicationID, "application-id", 0, "Filter by application id")
	cmd.Flags().StringVar(&opts.Status, "status", "", "Filter by status")
	if kind.PageList {
		cmd.Flags().StringVar(&opts.Keyword, "keyword", "", "Match the code or current name")
		cmd.Flags().StringVar(&opts.Category, "category", "", "Filter by category")
		cmd.Flags().IntVar(&opts.Page, "page", 0, "Zero-based page number")
		cmd.Flags().IntVar(&opts.Size, "size", 20, "Page size (server caps at 200)")
	}
	return cmd
}
